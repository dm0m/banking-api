package io.github.dm0m.bankingapi.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import io.github.dm0m.bankingapi.TestAccounts;
import io.github.dm0m.bankingapi.TestcontainersConfiguration;
import io.github.dm0m.bankingapi.account.AccountService;
import io.github.dm0m.bankingapi.customer.CustomerService;

// full stack against a real Postgres: HTTP status codes, JSON shape, real transactions
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TransferControllerIntegrationTest {

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
                        .with(admin())
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

        mockMvc.perform(get(location).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromAccountId").value(from))
                .andExpect(jsonPath("$.amount").value(30));
    }

    @Test
    void getTransferById_unknown_returns404() throws Exception {
        mockMvc.perform(get("/transfers/999999").with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void history_returnsSentAndReceivedTransfersNewestFirstInPageShape() throws Exception {
        Long account = createAccountWithBalance("100");
        Long other = createAccountWithBalance("100");
        postTransfer(account, other, "10").andExpect(status().isCreated());
        postTransfer(other, account, "20").andExpect(status().isCreated());
        postTransfer(account, other, "30").andExpect(status().isCreated());

        mockMvc.perform(get("/accounts/{id}/transfers", account).param("size", "2").with(admin()))
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
        mockMvc.perform(get("/accounts/999999/transfers").with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void history_unknownSortProperty_returns400() throws Exception {
        Long account = createAccountWithBalance("0");

        mockMvc.perform(get("/accounts/{id}/transfers", account).param("sort", "foo").with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("invalid sort property 'foo'"));
    }

    // ---- authorization: a USER may only act on its own accounts ----

    @Test
    void user_transferFromOwnAccount_returns201() throws Exception {
        Long from = createAccountWithBalance("100");
        Long to = createAccountWithBalance("0");

        postTransfer(userOwning(from), from, to, "30").andExpect(status().isCreated());

        assertThat(balanceOf(to)).isEqualByComparingTo("30");
    }

    @Test
    void user_transferFromSomeoneElsesAccount_returns403AndMovesNothing() throws Exception {
        Long victim = createAccountWithBalance("100");
        Long attacker = createAccountWithBalance("0");

        postTransfer(userOwning(attacker), victim, attacker, "30")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("access denied"));

        assertThat(balanceOf(victim)).isEqualByComparingTo("100");
        assertThat(transferCountFor(victim)).isZero();
    }

    @Test
    void user_transferFromUnknownAccount_returns403Not404() throws Exception {
        Long own = createAccountWithBalance("0");

        // 404 would tell the caller the id doesn't exist
        postTransfer(userOwning(own), 999_999L, own, "30").andExpect(status().isForbidden());
    }

    @Test
    void user_senderAndRecipientCanReadTheTransfer_strangerCannot() throws Exception {
        Long from = createAccountWithBalance("100");
        Long to = createAccountWithBalance("0");
        Long stranger = createAccountWithBalance("0");
        String location = postTransfer(from, to, "30").andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location).with(userOwning(from))).andExpect(status().isOk());
        mockMvc.perform(get(location).with(userOwning(to))).andExpect(status().isOk());
        mockMvc.perform(get(location).with(userOwning(stranger))).andExpect(status().isForbidden());
    }

    @Test
    void user_historyOfOwnAccount_returns200_ofAnotherAccount_returns403() throws Exception {
        Long own = createAccountWithBalance("0");
        Long other = createAccountWithBalance("0");

        mockMvc.perform(get("/accounts/{id}/transfers", own).with(userOwning(own))).andExpect(status().isOk());
        mockMvc.perform(get("/accounts/{id}/transfers", other).with(userOwning(own))).andExpect(status().isForbidden());
    }

    @Test
    void tokenWithoutRole_returns403() throws Exception {
        Long account = createAccountWithBalance("0");

        mockMvc.perform(get("/accounts/{id}/transfers", account).with(jwt())).andExpect(status().isForbidden());
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    // a USER token for the customer that owns the account, shaped like the ones AuthService issues
    private RequestPostProcessor userOwning(Long accountId) {
        Long customerId = accountService.getAccountById(accountId).customerId();
        return jwt().jwt(j -> j.claim("role", "USER").claim("customerId", customerId))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private ResultActions postTransfer(Long from, Long to, String amount) throws Exception {
        return postTransfer(admin(), from, to, amount);
    }

    private ResultActions postTransfer(RequestPostProcessor caller, Long from, Long to, String amount) throws Exception {
        return mockMvc.perform(post("/transfers")
                .with(caller)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fromAccountId\": %d, \"toAccountId\": %d, \"amount\": %s}".formatted(from, to, amount)));
    }

    private Long createAccountWithBalance(String balance) {
        return TestAccounts.openWithBalance(customerService, accountService, balance);
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
