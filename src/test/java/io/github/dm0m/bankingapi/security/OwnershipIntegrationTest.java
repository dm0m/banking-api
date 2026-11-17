package io.github.dm0m.bankingapi.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import io.github.dm0m.bankingapi.TestcontainersConfiguration;
import io.github.dm0m.bankingapi.account.AccountCurrency;
import io.github.dm0m.bankingapi.account.AccountService;
import io.github.dm0m.bankingapi.account.AccountType;
import io.github.dm0m.bankingapi.account.OpenAccountRequest;
import io.github.dm0m.bankingapi.customer.AddressRequest;
import io.github.dm0m.bankingapi.customer.CreateCustomerRequest;
import io.github.dm0m.bankingapi.customer.CustomerService;

// Who may call the customer and account endpoints: ADMIN anything, USER only its own records.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OwnershipIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private AccountService accountService;

    // ---- customers ----

    @Test
    void user_readsOwnCustomer_200_anotherCustomer_403() throws Exception {
        Long me = createCustomer();
        Long other = createCustomer();

        mockMvc.perform(get("/customers/{id}", me).with(user(me))).andExpect(status().isOk());
        mockMvc.perform(get("/customers/{id}", other).with(user(me)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("access denied"));
    }

    @Test
    void user_updatesAnotherCustomer_403() throws Exception {
        Long me = createCustomer();
        Long other = createCustomer();

        mockMvc.perform(patch("/customers/{id}", other).with(user(me))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"firstName\": \"Mallory\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void user_createsCustomer_403() throws Exception {
        // a valid body: @Valid runs before @PreAuthorize, so an invalid one would get 400 first
        String body = """
                {"firstName": "Eve", "lastName": "Doe", "phone": "+33612345678", "email": "%s",
                 "address": {"streetNo": "12", "street": "Rue de Rivoli", "city": "Paris",
                             "state": "Ile-de-France", "zipCode": "75001", "country": "FR"},
                 "dob": "1990-05-15"}
                """.formatted(UUID.randomUUID() + "@example.com");

        mockMvc.perform(post("/customers").with(user(createCustomer()))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void user_issuesActivationCode_403() throws Exception {
        Long me = createCustomer();

        mockMvc.perform(post("/customers/{id}/activation-code", me).with(user(me)))
                .andExpect(status().isForbidden());
    }

    // ---- accounts ----

    @Test
    void user_readsOwnAccount_200_anotherAccount_403() throws Exception {
        Long me = createCustomer();
        Long myAccount = openAccount(me);
        Long otherAccount = openAccount(createCustomer());

        mockMvc.perform(get("/accounts/{id}", myAccount).with(user(me))).andExpect(status().isOk());
        mockMvc.perform(get("/accounts/{id}", otherAccount).with(user(me))).andExpect(status().isForbidden());
    }

    @Test
    void user_whoseCustomerIdEqualsAnAccountId_cannotReadThatAccount() throws Exception {
        // the old check compared the token's customerId with the path's account id
        Long otherAccount = openAccount(createCustomer());

        mockMvc.perform(get("/accounts/{id}", otherAccount).with(user(otherAccount)))
                .andExpect(status().isForbidden());
    }

    @Test
    void user_listsOwnAccounts_200_anotherCustomersAccounts_403() throws Exception {
        Long me = createCustomer();
        openAccount(me);
        Long other = createCustomer();

        mockMvc.perform(get("/customers/{id}/accounts", me).with(user(me)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
        mockMvc.perform(get("/customers/{id}/accounts", other).with(user(me))).andExpect(status().isForbidden());
    }

    @Test
    void user_opensAccountForSelf_201_forAnotherCustomer_403() throws Exception {
        Long me = createCustomer();
        Long other = createCustomer();
        String body = "{\"accountType\": \"CHECKING\", \"currency\": \"EUR\"}";

        mockMvc.perform(post("/customers/{id}/accounts", me).with(user(me))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/customers/{id}/accounts", other).with(user(me))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void user_closesOwnAccount_200_anotherAccount_403() throws Exception {
        Long me = createCustomer();
        Long myAccount = openAccount(me);
        Long otherAccount = openAccount(createCustomer());

        mockMvc.perform(post("/accounts/{id}/close", otherAccount).with(user(me))).andExpect(status().isForbidden());
        mockMvc.perform(post("/accounts/{id}/close", myAccount).with(user(me))).andExpect(status().isOk());
    }

    @Test
    void deposit_user_403_admin_200() throws Exception {
        Long me = createCustomer();
        Long myAccount = openAccount(me);
        String body = "{\"amount\": 100}";

        // even into their own account: a deposit creates money
        mockMvc.perform(post("/accounts/{id}/deposit", myAccount).with(user(me))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/accounts/{id}/deposit", myAccount).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
    }

    @Test
    void admin_readsAnyCustomerAndAccount_200() throws Exception {
        Long customer = createCustomer();
        Long account = openAccount(customer);

        mockMvc.perform(get("/customers/{id}", customer).with(admin())).andExpect(status().isOk());
        mockMvc.perform(get("/accounts/{id}", account).with(admin())).andExpect(status().isOk());
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(j -> j.claim("role", "ADMIN")).authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    // shaped like the tokens AuthService issues
    private static RequestPostProcessor user(Long customerId) {
        return jwt().jwt(j -> j.claim("role", "USER").claim("customerId", customerId))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }

    private Long createCustomer() {
        AddressRequest address = new AddressRequest("12", "Rue de Rivoli", "Paris", "Ile-de-France", "75001", "FR");
        return customerService.create(new CreateCustomerRequest(
                "John", "Doe", "+33612345678", UUID.randomUUID() + "@example.com", address,
                LocalDate.of(1990, 5, 15))).id();
    }

    private Long openAccount(Long customerId) {
        return accountService.open(customerId, new OpenAccountRequest(AccountType.CHECKING, AccountCurrency.EUR)).id();
    }
}
