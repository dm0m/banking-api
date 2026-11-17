package io.github.dm0m.bankingapi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import io.github.dm0m.bankingapi.model.User;
import io.github.dm0m.bankingapi.model.enums.Role;
import io.github.dm0m.bankingapi.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    private static final String EMAIL = "admin@example.com";
    private static final String PASSWORD = "correct-horse-battery";

    @Mock
    private UserRepository userRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    @Test
    void nothingConfigured_createsNothing() throws Exception {
        bootstrap("", "").run(null);
        verify(userRepository, never()).save(any());
    }

    @Test
    void adminAlreadyExists_createsNothing() throws Exception {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(true);
        bootstrap(EMAIL, PASSWORD).run(null);
        verify(userRepository, never()).save(any());
    }

    @Test
    void noAdminYet_createsActivatedAdminWithHashedPasswordAndNoCustomer() throws Exception {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        bootstrap(" Admin@Example.com ", PASSWORD).run(null);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        User admin = saved.getValue();
        assertThat(admin.getEmail()).isEqualTo(EMAIL);
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getCustomer()).isNull();
        assertThat(admin.isActivated()).isTrue();
        assertThat(admin.getPasswordHash()).isNotEqualTo(PASSWORD);
        assertThat(passwordEncoder.matches(PASSWORD, admin.getPasswordHash())).isTrue();
    }

    @Test
    void passwordShorterThan12_failsStartup() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        assertThatThrownBy(() -> bootstrap(EMAIL, "a".repeat(11)).run(null))
                .isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void passwordLongerThan72_failsStartup() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> bootstrap(EMAIL, "a".repeat(73)).run(null))
                .isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void passwordWithoutEmail_failsStartup() {
        assertThatThrownBy(() -> bootstrap("", PASSWORD).run(null))
                .isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void emailWithoutPassword_failsStartup() {
        assertThatThrownBy(() -> bootstrap(EMAIL, "").run(null))
                .isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void emailAlreadyUsedByAnotherLogin_failsStartupWithClearMessage() {
        when(userRepository.existsByRole(Role.ADMIN)).thenReturn(false);
        when(userRepository.existsByEmail(EMAIL)).thenReturn(true);
        assertThatThrownBy(() -> bootstrap(" Admin@Example.com ", PASSWORD).run(null))
                .isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).save(any());
    }

    private AdminBootstrap bootstrap(String email, String password) {
        return new AdminBootstrap(userRepository, passwordEncoder, email, password);
    }
}
