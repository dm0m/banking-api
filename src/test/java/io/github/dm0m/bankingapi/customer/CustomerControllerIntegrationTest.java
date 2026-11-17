package io.github.dm0m.bankingapi.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
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

import com.jayway.jsonpath.JsonPath;

import io.github.dm0m.bankingapi.TestcontainersConfiguration;

// full stack against a real Postgres: HTTP status codes, JSON shape, validation and ProblemDetail errors.
// Who may call what is covered in OwnershipIntegrationTest; every call here is made as an admin.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CustomerControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    // ---- create ----

    @Test
    void create_valid_returns201WithLocationAndBody() throws Exception {
        String email = uniqueEmail();

        String body = postCustomer(customerJson(email, "1990-05-15", "FR"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("/customers/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.phone").value("+33612345678"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.dob").value("1990-05-15"))
                .andExpect(jsonPath("$.address.city").value("Paris"))
                .andExpect(jsonPath("$.address.country").value("FR"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.updatedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        Number id = JsonPath.read(body, "$.id");
        mockMvc.perform(get("/customers/{id}", id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void create_mixedCaseEmail_isStoredLowercase() throws Exception {
        String email = uniqueEmail();

        postCustomer(customerJson(email.toUpperCase(), "1990-05-15", "FR"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void create_emailAlreadyUsedInAnotherCase_returns409() throws Exception {
        String email = uniqueEmail();
        postCustomer(customerJson(email, "1990-05-15", "FR")).andExpect(status().isCreated());

        postCustomer(customerJson(email.toUpperCase(), "1990-05-15", "FR"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void create_minor_returns400WithDobError() throws Exception {
        String dob = LocalDate.now().minusYears(17).toString();

        postCustomer(customerJson(uniqueEmail(), dob, "FR"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("request validation failed"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("dob")));
    }

    @Test
    void create_invalidCountryCode_returns400WithNestedFieldError() throws Exception {
        postCustomer(customerJson(uniqueEmail(), "1990-05-15", "XX"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("address.country")));
    }

    @Test
    void create_missingFields_listsEveryRejectedField() throws Exception {
        postCustomer("{\"firstName\": \"\", \"email\": \"not-an-email\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("firstName")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("lastName")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("phone")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("email")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("address")))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("dob")));
    }

    @Test
    void create_malformedJson_returns400ProblemDetail() throws Exception {
        postCustomer("{\"firstName\": ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").isNotEmpty());
    }

    // ---- read ----

    @Test
    void get_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/customers/999999").with(admin()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").isNotEmpty());
    }

    @Test
    void get_nonNumericId_returns400() throws Exception {
        mockMvc.perform(get("/customers/abc").with(admin()))
                .andExpect(status().isBadRequest());
    }

    // ---- update ----

    @Test
    void patch_onlyGivenFieldsChange() throws Exception {
        Long id = createCustomer(uniqueEmail());

        patchCustomer(id, "{\"firstName\": \"Jane\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jane"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.phone").value("+33612345678"))
                .andExpect(jsonPath("$.address.city").value("Paris"));
    }

    @Test
    void patch_address_replacesTheWholeAddressInPlace() throws Exception {
        Long id = createCustomer(uniqueEmail());
        Long addressIdBefore = addressIdOf(id);

        patchCustomer(id, """
                {"address": {"streetNo": "5", "street": "Unter den Linden", "city": "Berlin",
                             "state": "Berlin", "zipCode": "10117", "country": "DE"}}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.address.street").value("Unter den Linden"))
                .andExpect(jsonPath("$.address.city").value("Berlin"))
                .andExpect(jsonPath("$.address.country").value("DE"));

        assertThat(addressIdOf(id)).isEqualTo(addressIdBefore);
    }

    @Test
    void patch_blankFirstName_returns400AndChangesNothing() throws Exception {
        Long id = createCustomer(uniqueEmail());

        patchCustomer(id, "{\"firstName\": \"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("firstName")));

        assertThat(customerService.getCustomerById(id).firstName()).isEqualTo("John");
    }

    @Test
    void patch_incompleteAddress_returns400() throws Exception {
        Long id = createCustomer(uniqueEmail());

        patchCustomer(id, "{\"address\": {\"city\": \"Berlin\"}}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("address.street")));
    }

    @Test
    void patch_emailOfAnotherCustomer_returns409() throws Exception {
        String taken = uniqueEmail();
        createCustomer(taken);
        Long id = createCustomer(uniqueEmail());

        patchCustomer(id, "{\"email\": \"%s\"}".formatted(taken.toUpperCase()))
                .andExpect(status().isConflict());
    }

    @Test
    void patch_unknownId_returns404() throws Exception {
        patchCustomer(999_999L, "{\"firstName\": \"Jane\"}")
                .andExpect(status().isNotFound());
    }

    @Test
    void patch_keepsCreatedAtAndMovesUpdatedAt() throws Exception {
        Long id = createCustomer(uniqueEmail());
        CustomerResponse before = customerService.getCustomerById(id);

        patchCustomer(id, "{\"firstName\": \"Jane\"}").andExpect(status().isOk());

        CustomerResponse after = customerService.getCustomerById(id);
        assertThat(after.createdAt()).isEqualTo(before.createdAt());
        assertThat(after.updatedAt()).isAfterOrEqualTo(before.updatedAt());
        // created_at is updatable = false, so not even a stray write can move it in the database
        assertThat(createdAtInDatabase(id)).isEqualTo(before.createdAt());
    }

    // ---- helpers ----

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private ResultActions postCustomer(String json) throws Exception {
        return mockMvc.perform(post("/customers")
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions patchCustomer(Long id, String json) throws Exception {
        return mockMvc.perform(patch("/customers/{id}", id)
                .with(admin())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private Long createCustomer(String email) {
        return customerService.create(new CreateCustomerRequest(
                "John", "Doe", "+33612345678", email,
                new AddressRequest("12", "Rue de Rivoli", "Paris", "Ile-de-France", "75001", "FR"),
                LocalDate.of(1990, 5, 15))).id();
    }

    private static String customerJson(String email, String dob, String country) {
        return """
                {
                  "firstName": "John",
                  "lastName": "Doe",
                  "phone": "+33612345678",
                  "email": "%s",
                  "dob": "%s",
                  "address": {"streetNo": "12", "street": "Rue de Rivoli", "city": "Paris",
                              "state": "Ile-de-France", "zipCode": "75001", "country": "%s"}
                }
                """.formatted(email, dob, country);
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    private Long addressIdOf(Long customerId) {
        return jdbcTemplate.queryForObject("SELECT address_id FROM customers WHERE id = ?", Long.class, customerId);
    }

    private Instant createdAtInDatabase(Long customerId) {
        return jdbcTemplate.queryForObject("SELECT created_at FROM customers WHERE id = ?", Instant.class, customerId);
    }
}
