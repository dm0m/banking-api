package io.github.dm0m.bankingapi.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import io.github.dm0m.bankingapi.TestcontainersConfiguration;
import io.github.dm0m.bankingapi.customer.AddressRequest;
import io.github.dm0m.bankingapi.customer.CreateCustomerRequest;
import io.github.dm0m.bankingapi.customer.CustomerService;

@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, LoginLockoutIntegrationTest.ClockOverride.class})
// window and lockout differ on purpose: swapping them in the code must fail a test
@TestPropertySource(properties = {
    "app.login.max-failures=5",
    "app.login.failure-window=15m",
    "app.login.lockout=20m"
})
class LoginLockoutIntegrationTest {

    private static final String PASSWORD = "correct-horse-battery";
    private static final String WRONG_PASSWORD = "wrong-password-123";
    private static final int MAX_FAILURES = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration LOCKOUT = Duration.ofMinutes(20);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private AuthService authService;
    @Autowired
    private LoginAttemptService loginAttemptService;
    @Autowired
    private LoginAttemptCleanup loginAttemptCleanup;
    @Autowired
    private LoginAttemptRepository loginAttemptRepository;
    @Autowired
    private MutableClock clock;

    @Test
    void login_afterMaxFailures_returns429EvenWithTheRightPassword() throws Exception {
        String email = activatedCustomer();
        failLogin(email, MAX_FAILURES);
        login(email, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, Long.toString(LOCKOUT.toSeconds() + 1)))
                .andExpect(jsonPath("$.detail").value("Too many failed login attempts"));
    }

    @Test
    void login_unknownEmail_locksLikeAKnownOne() throws Exception {
        String email = uniqueEmail();
        failLogin(email, MAX_FAILURES);
        login(email, PASSWORD)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, Long.toString(LOCKOUT.toSeconds() + 1)))
                .andExpect(jsonPath("$.detail").value("Too many failed login attempts"));
    }

    @Test
    void login_emailInAnotherCase_sharesTheCount() throws Exception {
        String email = activatedCustomer();
        failLogin(email.toUpperCase(), MAX_FAILURES);
        login(email, PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void login_lockedEmail_doesNotLockOthers() throws Exception {
        String other = activatedCustomer();
        failLogin(uniqueEmail(), MAX_FAILURES);
        login(other, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void login_success_resetsTheCount() throws Exception {
        String email = activatedCustomer();
        failLogin(email, MAX_FAILURES - 1);
        login(email, PASSWORD).andExpect(status().isOk());
        failLogin(email, MAX_FAILURES - 1);
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void login_lockExpires_rightPasswordWorksAgain() throws Exception {
        String email = activatedCustomer();
        failLogin(email, MAX_FAILURES);
        clock.advance(LOCKOUT.minusSeconds(1));
        login(email, PASSWORD).andExpect(status().isTooManyRequests());
        clock.advance(Duration.ofSeconds(1));
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void login_attemptsWhileLocked_dontExtendTheLock() throws Exception {
        String email = activatedCustomer();
        failLogin(email, MAX_FAILURES);
        clock.advance(LOCKOUT.minusSeconds(1));
        login(email, WRONG_PASSWORD).andExpect(status().isTooManyRequests());
        clock.advance(Duration.ofSeconds(1));
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void login_failureAfterTheLockExpired_startsANewCount() throws Exception {
        String email = activatedCustomer();
        failLogin(email, MAX_FAILURES);
        clock.advance(LOCKOUT);
        failLogin(email, MAX_FAILURES - 1);
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void login_windowOver_startsANewCount() throws Exception {
        String email = activatedCustomer();
        failLogin(email, MAX_FAILURES - 1);
        clock.advance(WINDOW);
        failLogin(email, MAX_FAILURES - 1);
        login(email, PASSWORD).andExpect(status().isOk());
    }

    @Test
    void login_windowStillOpen_keepsCounting() throws Exception {
        String email = activatedCustomer();
        failLogin(email, MAX_FAILURES - 1);
        clock.advance(WINDOW.minusSeconds(1));
        failLogin(email, 1);
        login(email, PASSWORD).andExpect(status().isTooManyRequests());
    }

    @Test
    void recordFailure_inParallel_countsEveryFailure() throws Exception {
        String email = uniqueEmail();
        int threads = 10;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                results.add(executor.submit(() -> {
                    start.await();
                    loginAttemptService.recordFailure(email);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }
        LoginAttempt attempt = loginAttemptRepository.findById(email).orElseThrow();
        assertThat(attempt.getFailedCount()).isEqualTo(threads);
        assertThat(attempt.getLockedUntil()).isEqualTo(clock.instant().plus(LOCKOUT));
    }

    @Test
    void cleanup_deletesARowOnlyOnceItsWindowAndLockAreOver() throws Exception {
        String unlocked = uniqueEmail();
        String locked = uniqueEmail();
        failLogin(unlocked, 1);
        failLogin(locked, MAX_FAILURES);
        clock.advance(WINDOW.minusSeconds(1));
        loginAttemptCleanup.deleteExpiredAttempts();
        assertThat(loginAttemptRepository.existsById(unlocked)).isTrue();
        clock.advance(Duration.ofSeconds(1));
        loginAttemptCleanup.deleteExpiredAttempts();
        assertThat(loginAttemptRepository.existsById(unlocked)).isFalse();
        assertThat(loginAttemptRepository.existsById(locked)).isTrue();
        clock.advance(LOCKOUT.minus(WINDOW));
        loginAttemptCleanup.deleteExpiredAttempts();
        assertThat(loginAttemptRepository.existsById(locked)).isFalse();
    }

    @Test
    void login_emailTooLong_returns400() throws Exception {
        login("a".repeat(256) + "@example.com", WRONG_PASSWORD).andExpect(status().isBadRequest());
    }

    private void failLogin(String email, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            login(email, WRONG_PASSWORD).andExpect(status().isUnauthorized());
        }
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    private String activatedCustomer() {
        String email = uniqueEmail();
        AddressRequest address = new AddressRequest("12","Rue de Rivoli", "Paris", "Ile-de-France", "75001", "FR");
        Long customerId = customerService.create(new CreateCustomerRequest(
                "John", "Doe", "+33612345678", email, address, LocalDate.of(1990, 5, 15))).id();
        String code = authService.issueActivationCode(customerId).activationCode();
        authService.register(new RegisterRequest(email, code, PASSWORD));
        return email;
    }

    private static String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockOverride {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        }
    }

    static class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public Instant instant() {
            return now;
        }
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }
        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }
    }
}
