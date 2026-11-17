package io.github.dm0m.bankingapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.nimbusds.jose.jwk.source.ImmutableSecret;

import io.github.dm0m.bankingapi.dto.LoginRequest;
import io.github.dm0m.bankingapi.dto.RegisterRequest;
import io.github.dm0m.bankingapi.exception.InvalidActivationCodeException;
import io.github.dm0m.bankingapi.model.User;
import io.github.dm0m.bankingapi.model.enums.Role;
import io.github.dm0m.bankingapi.repository.CustomerRepository;
import io.github.dm0m.bankingapi.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-15T10:00:00Z");
    private static final SecretKey KEY = new SecretKeySpec(
            "unit-test-jwt-secret-at-least-32-bytes".getBytes(StandardCharsets.UTF_8), "HmacSHA256");

    @Mock
    private UserRepository userRepository;
    @Mock
    private CustomerRepository customerRepository;
    // cost 4: the minimum, keeps the tests fast
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, customerRepository, passwordEncoder,
                Clock.fixed(NOW, ZoneOffset.UTC), new NimbusJwtEncoder(new ImmutableSecret<>(KEY)),
                Duration.ofMinutes(15), Duration.ofHours(72));
    }

    @Test
    void register_expiredCode_throwsAndLeavesPasswordUnset() {
        User user = pendingUser("alice@example.com", "the-code", NOW.minusSeconds(1));
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.register(
                new RegisterRequest("alice@example.com", "the-code", "correct-horse-battery")))
                .isInstanceOf(InvalidActivationCodeException.class);
        assertThat(user.isActivated()).isFalse();
    }

    @Test
    void register_codeExpiringRightNow_isStillAccepted() {
        User user = pendingUser("alice@example.com", "the-code", NOW);
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(user));

        authService.register(new RegisterRequest("alice@example.com", "the-code", "correct-horse-battery"));

        assertThat(user.isActivated()).isTrue();
        assertThat(user.getActivationCodeHash()).isNull();
        assertThat(user.getActivationExpiresAt()).isNull();
    }

    @Test
    void login_admin_tokenHasRoleButNoCustomerId() {
        User admin = new User();
        ReflectionTestUtils.setField(admin, "id", 7L);
        admin.setEmail("admin@example.com");
        admin.setRole(Role.ADMIN);
        admin.setPasswordHash(passwordEncoder.encode("admin-password-123"));
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(admin));

        String token = authService.login(new LoginRequest("admin@example.com", "admin-password-123")).accessToken();

        Jwt jwt = decode(token);
        assertThat(jwt.getSubject()).isEqualTo("7");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(jwt.hasClaim("customerId")).isFalse();
        assertThat(jwt.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    private User pendingUser(String email, String code, Instant expiresAt) {
        User user = new User();
        user.setEmail(email);
        user.setRole(Role.USER);
        user.setActivationCodeHash(passwordEncoder.encode(code));
        user.setActivationExpiresAt(expiresAt);
        return user;
    }

    private Jwt decode(String token) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(KEY).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
        return decoder.decode(token);
    }
}
