package io.github.dm0m.bankingapi.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import io.github.dm0m.bankingapi.TestcontainersConfiguration;

// 401s are written by the security filter chain, not by GlobalExceptionHandler:
// check they still come back as ProblemDetail, with the RFC 6750 WWW-Authenticate header.
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecurityErrorsIntegrationTest {

    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtEncoder jwtEncoder;

    @Test
    void noToken_returns401ProblemDetail_withPlainBearerChallenge() throws Exception {
        mockMvc.perform(get("/customers/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.title").value("Unauthorized"))
                .andExpect(jsonPath("$.detail").value("authentication required"))
                .andExpect(jsonPath("$.instance").value("/customers/1"))
                // RFC 6750: no error code when the request carried no credentials at all
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    void malformedToken_returns401InvalidToken() throws Exception {
        expectInvalidToken(getWithToken("garbage"));
    }

    @Test
    void expiredToken_returns401InvalidToken() throws Exception {
        // beyond the decoder's 60s clock skew
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
        String token = sign(jwtEncoder, past, past.plus(15, ChronoUnit.MINUTES));

        expectInvalidToken(getWithToken(token));
    }

    @Test
    void tokenSignedWithAnotherKey_returns401InvalidToken() throws Exception {
        JwtEncoder foreign = new NimbusJwtEncoder(new ImmutableSecret<>(new SecretKeySpec(
                "some-other-secret-at-least-32-bytes-long".getBytes(StandardCharsets.UTF_8), "HmacSHA256")));
        Instant now = Instant.now();

        expectInvalidToken(getWithToken(sign(foreign, now, now.plus(15, ChronoUnit.MINUTES))));
    }

    @Test
    void invalidTokenDetail_doesNotLeakDecoderMessages() throws Exception {
        getWithToken("garbage")
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        not(containsString("Malformed"))));
    }

    @Test
    void preAuthorizeDenial_returns403WithTheSameShape() throws Exception {
        // a USER reading a customer that isn't theirs: denied inside the controller call
        mockMvc.perform(get("/customers/{id}", 999_999L)
                        .with(jwt().jwt(j -> j.claim("customerId", 1L)).authorities(new SimpleGrantedAuthority("ROLE_USER"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Forbidden"))
                .andExpect(jsonPath("$.detail").value("access denied"))
                .andExpect(jsonPath("$.instance").value("/customers/999999"));
    }

    private ResultActions getWithToken(String token) throws Exception {
        return mockMvc.perform(get("/customers/1").header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    private static void expectInvalidToken(ResultActions result) throws Exception {
        result.andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.detail").value("invalid or expired token"))
                .andExpect(jsonPath("$.instance").value("/customers/1"))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE,
                        startsWith("Bearer error=\"invalid_token\"")));
    }

    private static String sign(JwtEncoder encoder, Instant issuedAt, Instant expiresAt) {
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("banking-api")
                .subject("1")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .claim("role", "ADMIN")
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
