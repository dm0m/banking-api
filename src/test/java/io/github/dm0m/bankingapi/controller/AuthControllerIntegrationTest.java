package io.github.dm0m.bankingapi.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import io.github.dm0m.bankingapi.TestcontainersConfiguration;
import io.github.dm0m.bankingapi.dto.AddressRequest;
import io.github.dm0m.bankingapi.dto.CreateCustomerRequest;
import io.github.dm0m.bankingapi.dto.UpdateCustomerRequest;
import io.github.dm0m.bankingapi.service.CustomerService;

// Activation and login over HTTP against a real Postgres, ending with a real signed token
// that goes through the real filter chain (no jwt() shortcut).
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AuthControllerIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CustomerService customerService;

    @Test
    void fullFlow_issueRegisterLogin_tokenOpensProtectedEndpoint() throws Exception {
        String email = uniqueEmail();
        Long customerId = createCustomer(email);

        String code = issueCode(customerId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activationCode").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        register(email, JsonPath.read(code, "$.activationCode"), PASSWORD).andExpect(status().isNoContent());

        String token = loginOk(email, PASSWORD);

        mockMvc.perform(get("/customers/{id}", customerId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customerId));
    }

    @Test
    void login_returnsBearerTokenWithConfiguredLifetime() throws Exception {
        String email = activatedCustomer();

        login(email, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void protectedEndpoint_noToken_returns401() throws Exception {
        mockMvc.perform(get("/customers/1")).andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpoint_tamperedToken_returns401() throws Exception {
        String token = loginOk(activatedCustomer(), PASSWORD);
        // flip one character of the signature
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');

        mockMvc.perform(get("/customers/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_wrongCode_returns400() throws Exception {
        String email = uniqueEmail();
        issueCodeValue(createCustomer(email));

        register(email, "not-the-code", PASSWORD).andExpect(status().isBadRequest());
        login(email, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void register_codeUsedTwice_returns400() throws Exception {
        String email = uniqueEmail();
        String code = issueCodeValue(createCustomer(email));
        register(email, code, PASSWORD).andExpect(status().isNoContent());

        // a second use must not let anyone overwrite the password
        register(email, code, "another-password-123").andExpect(status().isBadRequest());
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void register_unknownEmail_returns400LikeAWrongCode() throws Exception {
        register(uniqueEmail(), "whatever", PASSWORD)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid or expired activation code"));
    }

    @Test
    void register_passwordTooShort_returns400() throws Exception {
        String email = uniqueEmail();
        String code = issueCodeValue(createCustomer(email));

        register(email, code, "short").andExpect(status().isBadRequest());
    }

    @Test
    void register_emailInDifferentCase_activates() throws Exception {
        String email = uniqueEmail();
        String code = issueCodeValue(createCustomer(email));

        register(email.toUpperCase(), code, PASSWORD).andExpect(status().isNoContent());
        login(email.toUpperCase(), PASSWORD).andExpect(status().isOk());
    }

    @Test
    void issueCode_again_replacesTheEarlierCode() throws Exception {
        String email = uniqueEmail();
        Long customerId = createCustomer(email);
        String first = issueCodeValue(customerId);
        String second = issueCodeValue(customerId);

        register(email, first, PASSWORD).andExpect(status().isBadRequest());
        register(email, second, PASSWORD).andExpect(status().isNoContent());
    }

    @Test
    void issueCode_customerAlreadyActivated_returns409() throws Exception {
        String email = uniqueEmail();
        Long customerId = createCustomer(email);
        register(email, issueCodeValue(customerId), PASSWORD).andExpect(status().isNoContent());

        issueCode(customerId).andExpect(status().isConflict());
    }

    @Test
    void issueCode_unknownCustomer_returns404() throws Exception {
        issueCode(999_999L).andExpect(status().isNotFound());
    }

    @Test
    void login_beforeActivation_returns401() throws Exception {
        String email = uniqueEmail();
        issueCodeValue(createCustomer(email));

        login(email, PASSWORD).andExpect(status().isUnauthorized());
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        login(activatedCustomer(), "wrong-password-123")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void login_unknownEmail_returns401WithTheSameMessage() throws Exception {
        login(uniqueEmail(), PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password"));
    }

    @Test
    void customerEmailChange_movesTheLoginToTheNewEmail() throws Exception {
        String oldEmail = uniqueEmail();
        Long customerId = createCustomer(oldEmail);
        register(oldEmail, issueCodeValue(customerId), PASSWORD).andExpect(status().isNoContent());
        String newEmail = uniqueEmail();

        customerService.update(customerId, new UpdateCustomerRequest(null, null, null, newEmail, null));

        login(newEmail, PASSWORD).andExpect(status().isOk());
        login(oldEmail, PASSWORD).andExpect(status().isUnauthorized());
    }

    // TODO step 4: a non-admin token must get 403 here

    private ResultActions issueCode(Long customerId) throws Exception {
        return mockMvc.perform(post("/customers/{id}/activation-code", customerId)
                .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private String issueCodeValue(Long customerId) throws Exception {
        String body = issueCode(customerId).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.activationCode");
    }

    private ResultActions register(String email, String code, String password) throws Exception {
        return mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"activationCode\": \"%s\", \"password\": \"%s\"}"
                        .formatted(email, code, password)));
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    private String loginOk(String email, String password) throws Exception {
        String body = login(email, password).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    /** A customer whose login is activated with {@link #PASSWORD}; returns its email. */
    private String activatedCustomer() throws Exception {
        String email = uniqueEmail();
        register(email, issueCodeValue(createCustomer(email)), PASSWORD).andExpect(status().isNoContent());
        return email;
    }

    private Long createCustomer(String email) {
        AddressRequest address = new AddressRequest("12", "Rue de Rivoli", "Paris", "Ile-de-France", "75001", "FR");
        return customerService.create(new CreateCustomerRequest(
                "John", "Doe", "+33612345678", email, address, LocalDate.of(1990, 5, 15))).id();
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }
}
