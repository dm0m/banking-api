package io.github.dm0m.bankingapi.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
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
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import io.github.dm0m.bankingapi.TestAccounts;
import io.github.dm0m.bankingapi.TestcontainersConfiguration;
import io.github.dm0m.bankingapi.customer.AddressRequest;
import io.github.dm0m.bankingapi.customer.CreateCustomerRequest;
import io.github.dm0m.bankingapi.customer.CustomerService;

// full stack against a real Postgres: HTTP status codes, JSON shape, validation and ProblemDetail errors.
// Who may call what is covered in OwnershipIntegrationTest; every call here is made as an admin.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AccountControllerIntegrationTest {

    // largest balance NUMERIC(19,4) holds in its integer part
    private static final String MAX_BALANCE = "999999999999999.0000";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---- open ----

    @Test
    void open_valid_returns201WithLocationAndActiveZeroBalanceAccount() throws Exception {
        Long customerId = createCustomer("FR");

        openAccount(customerId, "{\"accountType\": \"SAVING\", \"currency\": \"USD\"}")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/accounts/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.customerId").value(customerId))
                // FR IBAN: country, 2 check digits, 23-digit BBAN
                .andExpect(jsonPath("$.accountNumber").value(matchesPattern("FR\\d{25}")))
                .andExpect(jsonPath("$.balance").value(0))
                .andExpect(jsonPath("$.accountType").value("SAVING"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty());
    }

    @Test
    void open_ibanFollowsTheCustomersCountry() throws Exception {
        Long customerId = createCustomer("DE");

        openAccount(customerId, checkingEur())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountNumber").value(matchesPattern("DE\\d{20}")));
    }

    @Test
    void open_countryWithoutIbanFormat_returns422() throws Exception {
        Long customerId = createCustomer("US"); // a valid ISO code, but no IBAN

        openAccount(customerId, checkingEur())
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("Accounts cannot be opened for country 'US'"));
    }

    @Test
    void open_missingFields_returns400WithFieldErrors() throws Exception {
        Long customerId = createCustomer("FR");

        openAccount(customerId, "{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("accountType")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("currency")));
    }

    @Test
    void open_unknownCurrency_returns400() throws Exception {
        Long customerId = createCustomer("FR");

        openAccount(customerId, "{\"accountType\": \"CHECKING\", \"currency\": \"GBP\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void open_unknownCustomer_returns404() throws Exception {
        openAccount(999_999L, checkingEur()).andExpect(status().isNotFound());
    }

    // ---- read ----

    @Test
    void get_existing_returns200() throws Exception {
        Long accountId = createAccountWithBalance("25.5");

        mockMvc.perform(get("/accounts/{id}", accountId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accountId))
                .andExpect(jsonPath("$.balance").value(25.5));
    }

    @Test
    void get_unknown_returns404() throws Exception {
        mockMvc.perform(get("/accounts/999999").with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_returnsOnlyThatCustomersAccounts() throws Exception {
        Long customerId = createCustomer("FR");
        Long first = accountService.open(customerId, new OpenAccountRequest(AccountType.CHECKING, AccountCurrency.EUR)).id();
        Long second = accountService.open(customerId, new OpenAccountRequest(AccountType.SAVING, AccountCurrency.EUR)).id();
        createAccountWithBalance("0"); // someone else's

        mockMvc.perform(get("/customers/{id}/accounts", customerId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].id").value(containsInAnyOrder(first.intValue(), second.intValue())));
    }

    @Test
    void list_customerWithoutAccounts_returnsEmptyArray() throws Exception {
        Long customerId = createCustomer("FR");

        mockMvc.perform(get("/customers/{id}/accounts", customerId).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void list_unknownCustomer_returns404() throws Exception {
        mockMvc.perform(get("/customers/999999/accounts").with(admin()))
                .andExpect(status().isNotFound());
    }

    // ---- deposit ----

    @Test
    void deposit_valid_returns200AndAddsToBalance() throws Exception {
        Long accountId = createAccountWithBalance("10");

        deposit(accountId, "100.5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(110.5));

        assertThat(balanceOf(accountId)).isEqualByComparingTo("110.5");
    }

    @Test
    void deposit_zeroOrNegative_returns400() throws Exception {
        Long accountId = createAccountWithBalance("10");

        deposit(accountId, "0").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("amount")));
        deposit(accountId, "-5").andExpect(status().isBadRequest());

        assertThat(balanceOf(accountId)).isEqualByComparingTo("10");
    }

    @Test
    void deposit_moreThanFourDecimals_returns400() throws Exception {
        Long accountId = createAccountWithBalance("0");

        deposit(accountId, "1.00001").andExpect(status().isBadRequest());
    }

    @Test
    void deposit_missingAmount_returns400() throws Exception {
        Long accountId = createAccountWithBalance("0");

        mockMvc.perform(post("/accounts/{id}/deposit", accountId)
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("amount")));
    }

    @Test
    void deposit_closedAccount_returns409AndChangesNothing() throws Exception {
        Long accountId = createAccountWithBalance("0");
        accountService.close(accountId);

        deposit(accountId, "10").andExpect(status().isConflict());

        assertThat(balanceOf(accountId)).isEqualByComparingTo("0");
    }

    @Test
    void deposit_pastTheSupportedRange_returns422AndChangesNothing() throws Exception {
        Long accountId = createAccountWithBalance(MAX_BALANCE);

        deposit(accountId, "1")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("the resulting balance exceeds the supported range"));

        assertThat(balanceOf(accountId)).isEqualByComparingTo(MAX_BALANCE);
    }

    @Test
    void deposit_unknownAccount_returns404() throws Exception {
        deposit(999_999L, "10").andExpect(status().isNotFound());
    }

    // ---- close ----

    @Test
    void close_zeroBalance_returns200Inactive() throws Exception {
        Long accountId = createAccountWithBalance("0");

        close(accountId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("INACTIVE"));

        assertThat(statusOf(accountId)).isEqualTo("INACTIVE");
    }

    @Test
    void close_nonZeroBalance_returns409AndStaysActive() throws Exception {
        Long accountId = createAccountWithBalance("0.0001");

        close(accountId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("balance is not zero")));

        assertThat(statusOf(accountId)).isEqualTo("ACTIVE");
    }

    @Test
    void close_twice_returns409() throws Exception {
        Long accountId = createAccountWithBalance("0");
        close(accountId).andExpect(status().isOk());

        close(accountId)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("already closed")));
    }

    @Test
    void close_unknownAccount_returns404() throws Exception {
        close(999_999L).andExpect(status().isNotFound());
    }

    // ---- helpers ----

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static String checkingEur() {
        return "{\"accountType\": \"CHECKING\", \"currency\": \"EUR\"}";
    }

    private ResultActions openAccount(Long customerId, String json) throws Exception {
        return mockMvc.perform(post("/customers/{id}/accounts", customerId)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions deposit(Long accountId, String amount) throws Exception {
        return mockMvc.perform(post("/accounts/{id}/deposit", accountId)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"amount\": %s}".formatted(amount)));
    }

    private ResultActions close(Long accountId) throws Exception {
        return mockMvc.perform(post("/accounts/{id}/close", accountId).with(admin()));
    }

    private Long createCustomer(String country) {
        return customerService.create(new CreateCustomerRequest(
                "John", "Doe", "+33612345678", UUID.randomUUID() + "@example.com",
                new AddressRequest("12", "Main Street", "Somewhere", "Region", "12345", country),
                LocalDate.of(1990, 5, 15))).id();
    }

    private Long createAccountWithBalance(String balance) {
        return TestAccounts.openWithBalance(customerService, accountService, balance);
    }

    private BigDecimal balanceOf(Long accountId) {
        return jdbcTemplate.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, accountId);
    }

    private String statusOf(Long accountId) {
        return jdbcTemplate.queryForObject("SELECT account_status FROM accounts WHERE id = ?", String.class, accountId);
    }
}
