package io.github.dm0m.bankingapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.github.dm0m.bankingapi.dto.AddressRequest;
import io.github.dm0m.bankingapi.dto.CreateCustomerRequest;
import io.github.dm0m.bankingapi.dto.OpenAccountRequest;
import io.github.dm0m.bankingapi.model.enums.AccountCurrency;
import io.github.dm0m.bankingapi.model.enums.AccountType;
import io.github.dm0m.bankingapi.service.AccountService;
import io.github.dm0m.bankingapi.service.CustomerService;

// full stack against a real Postgres: HTTP status codes, JSON shape, real transactions
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TransferControllerIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void transfer_valid_returns201WithLocationAndMovesMoney() throws Exception {
        Long from = createAccountWithBalance("100");
        Long to = createAccountWithBalance("50");

        postTransfer(from, to, "30")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/transfers/")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.fromAccountId").value(from))
                .andExpect(jsonPath("$.toAccountId").value(to))
                .andExpect(jsonPath("$.amount").value(30))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());

        assertThat(balanceOf(from)).isEqualByComparingTo("70");
        assertThat(balanceOf(to)).isEqualByComparingTo("80");
    }

    @Test
    void transfer_missingAmount_returns400() throws Exception {
        Long from = createAccountWithBalance("100");
        Long to = createAccountWithBalance("50");

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fromAccountId\": %d, \"toAccountId\": %d}".formatted(from, to)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transfer_insufficientFunds_returns422AndChangesNothing() throws Exception {
        Long from = createAccountWithBalance("10");
        Long to = createAccountWithBalance("50");

        postTransfer(from, to, "30").andExpect(status().isUnprocessableEntity());

        assertThat(balanceOf(from)).isEqualByComparingTo("10");
        assertThat(balanceOf(to)).isEqualByComparingTo("50");
    }

    @Test
    void transfer_databaseFailsMidTransfer_rollsBackBothBalances() throws Exception {
        // the credit overflows NUMERIC(19,4), so Postgres rejects the write after the debit was applied in memory
        Long from = createAccountWithBalance("100");
        Long to = createAccountWithBalance("999999999999999.0000");

        ResultActions result = postTransfer(from, to, "30");

        assertThat(result.andReturn().getResponse().getStatus()).isNotEqualTo(201);
        assertThat(balanceOf(from)).isEqualByComparingTo("100");
        assertThat(balanceOf(to)).isEqualByComparingTo("999999999999999.0000");
        assertThat(transferCountFor(from)).isZero();
    }

    @Test
    void getTransferById_existing_returns200() throws Exception {
        Long from = createAccountWithBalance("100");
        Long to = createAccountWithBalance("0");
        String location = postTransfer(from, to, "30").andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromAccountId").value(from))
                .andExpect(jsonPath("$.amount").value(30));
    }

    @Test
    void getTransferById_unknown_returns404() throws Exception {
        mockMvc.perform(get("/transfers/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void history_returnsSentAndReceivedTransfersNewestFirstInPageShape() throws Exception {
        Long account = createAccountWithBalance("100");
        Long other = createAccountWithBalance("100");
        postTransfer(account, other, "10").andExpect(status().isCreated());
        postTransfer(other, account, "20").andExpect(status().isCreated());
        postTransfer(account, other, "30").andExpect(status().isCreated());

        mockMvc.perform(get("/accounts/{id}/transfers", account).param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[0].amount").value(30))
                .andExpect(jsonPath("$.content[1].amount").value(20))
                .andExpect(jsonPath("$.page.size").value(2))
                .andExpect(jsonPath("$.page.number").value(0))
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.page.totalPages").value(2));
    }

    @Test
    void history_unknownAccount_returns404() throws Exception {
        mockMvc.perform(get("/accounts/999999/transfers"))
                .andExpect(status().isNotFound());
    }

    @Test
    void history_unknownSortProperty_returns400() throws Exception {
        Long account = createAccountWithBalance("0");

        mockMvc.perform(get("/accounts/{id}/transfers", account).param("sort", "foo"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("invalid sort property 'foo'"));
    }

    private ResultActions postTransfer(Long from, Long to, String amount) throws Exception {
        return mockMvc.perform(post("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fromAccountId\": %d, \"toAccountId\": %d, \"amount\": %s}".formatted(from, to, amount)));
    }

    // open() starts every account at zero, so the balance is set directly
    private Long createAccountWithBalance(String balance) {
        AddressRequest address = new AddressRequest("12", "Rue de Rivoli", "Paris", "Ile-de-France", "75001", "FR");
        Long customerId = customerService.create(new CreateCustomerRequest(
                "John", "Doe", "+33612345678", UUID.randomUUID() + "@example.com",
                address, LocalDate.of(1990, 5, 15))).id();
        Long accountId = accountService.open(customerId,
                new OpenAccountRequest(AccountType.CHECKING, AccountCurrency.EUR)).id();
        jdbcTemplate.update("UPDATE accounts SET balance = ? WHERE id = ?", new BigDecimal(balance), accountId);
        return accountId;
    }

    private BigDecimal balanceOf(Long accountId) {
        return jdbcTemplate.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, accountId);
    }

    private int transferCountFor(Long accountId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfers WHERE from_account_id = ? OR to_account_id = ?",
                Integer.class, accountId, accountId);
        return count != null ? count : 0;
    }
}
