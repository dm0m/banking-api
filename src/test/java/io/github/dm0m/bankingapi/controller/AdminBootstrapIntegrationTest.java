package io.github.dm0m.bankingapi.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import io.github.dm0m.bankingapi.TestcontainersConfiguration;

@SpringBootTest(properties = {
        "app.admin.email=bootstrap-admin@example.com",
        "app.admin.password=bootstrap-admin-password"
})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminBootstrapIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void bootstrappedAdmin_canLogIn_andIsTreatedAsAdmin() throws Exception {
        String body = mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "bootstrap-admin@example.com", "password": "bootstrap-admin-password"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.accessToken");

        mockMvc.perform(get("/customers/{id}", 999_999L).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
