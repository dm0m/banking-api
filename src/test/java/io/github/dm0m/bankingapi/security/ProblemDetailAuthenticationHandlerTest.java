package io.github.dm0m.bankingapi.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

// The filter-level 403 (a URL rule denying access) has no endpoint to trigger it yet, so call the handler directly.
class ProblemDetailAuthenticationHandlerTest {

    // built like Spring's own ObjectMapper, which flattens ProblemDetail's extra properties
    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private final ProblemDetailAuthenticationHandler handler = new ProblemDetailAuthenticationHandler(objectMapper);
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/accounts/7");
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @Test
    void accessDenied_writes403ProblemDetail() throws Exception {
        handler.handle(request, response, new AccessDeniedException("Access Denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        JsonNode body = body();
        assertThat(body.get("status").asInt()).isEqualTo(403);
        assertThat(body.get("detail").asText()).isEqualTo("access denied");
        assertThat(body.get("instance").asText()).isEqualTo("/accounts/7");
        assertThat(body.has("properties")).isFalse();
    }

    @Test
    void missingCredentials_writes401WithPlainBearerChallenge() throws Exception {
        handler.commence(request, response, new InsufficientAuthenticationException("Full authentication is required"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        assertThat(body().get("detail").asText()).isEqualTo("authentication required");
    }

    @Test
    void invalidToken_writes401WithInvalidTokenChallenge_andHidesTheCause() throws Exception {
        handler.commence(request, response, new InvalidBearerTokenException("Jwt expired at 2026-01-01T00:00:00Z"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).startsWith("Bearer error=\"invalid_token\"");
        assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).doesNotContain("expired at");
        assertThat(body().get("detail").asText()).isEqualTo("invalid or expired token");
        assertThat(response.getContentAsString()).doesNotContain("expired at");
    }

    private JsonNode body() throws Exception {
        return objectMapper.readTree(response.getContentAsString());
    }
}
