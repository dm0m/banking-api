package io.github.dm0m.bankingapi.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import io.github.dm0m.bankingapi.account.AccountRepository;
import io.github.dm0m.bankingapi.transfer.TransferRepository;

class AccessGuardTest {

    private AccountRepository accountRepository;
    private TransferRepository transferRepository;
    private AccessGuard guard;

    @BeforeEach
    void setUp() {
        accountRepository = mock(AccountRepository.class);
        transferRepository = mock(TransferRepository.class);
        guard = new AccessGuard(accountRepository, transferRepository);
    }

    @Test
    void ownsAccount_asksTheRepositoryWithTheTokensCustomerId() {
        when(accountRepository.existsByIdAndCustomerId(10L, 7L)).thenReturn(true);

        assertThat(guard.ownsAccount(userToken(7L), 10L)).isTrue();
        assertThat(guard.ownsAccount(userToken(8L), 10L)).isFalse();
    }

    @Test
    void ownsAccount_acceptsAnyNumericClaimType() {
        // JSON numbers may be parsed as Integer or Long depending on their size
        when(accountRepository.existsByIdAndCustomerId(10L, 7L)).thenReturn(true);

        assertThat(guard.ownsAccount(tokenWithCustomerId(7), 10L)).isTrue();
    }

    @Test
    void ownsAccount_tokenWithoutCustomerId_isFalseWithoutQuerying() {
        // an admin token has no customerId; admins are let through by hasRole('ADMIN'), not by this check
        assertThat(guard.ownsAccount(tokenWithCustomerId(null), 10L)).isFalse();

        verify(accountRepository, never()).existsByIdAndCustomerId(any(), any());
    }

    @Test
    void ownsAccount_nonNumericCustomerId_isFalse() {
        assertThat(guard.ownsAccount(tokenWithCustomerId("7"), 10L)).isFalse();

        verify(accountRepository, never()).existsByIdAndCustomerId(any(), any());
    }

    @Test
    void ownsAccount_notAJwt_isFalse() {
        var other = new UsernamePasswordAuthenticationToken("someone", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));

        assertThat(guard.ownsAccount(other, 10L)).isFalse();
        assertThat(guard.ownsAccount(null, 10L)).isFalse();
    }

    @Test
    void ownsAccount_nullAccountId_isFalseWithoutQuerying() {
        assertThat(guard.ownsAccount(userToken(7L), null)).isFalse();

        verify(accountRepository, never()).existsByIdAndCustomerId(any(), any());
    }

    @Test
    void canSeeTransfer_delegatesToInvolvesCustomer() {
        when(transferRepository.involvesCustomer(3L, 7L)).thenReturn(true);

        assertThat(guard.canSeeTransfer(userToken(7L), 3L)).isTrue();
        assertThat(guard.canSeeTransfer(userToken(8L), 3L)).isFalse();
    }

    @Test
    void canSeeTransfer_noCustomerIdOrNoTransferId_isFalseWithoutQuerying() {
        assertThat(guard.canSeeTransfer(tokenWithCustomerId(null), 3L)).isFalse();
        assertThat(guard.canSeeTransfer(userToken(7L), null)).isFalse();

        verify(transferRepository, never()).involvesCustomer(any(), any());
    }

    private static JwtAuthenticationToken userToken(Long customerId) {
        return tokenWithCustomerId(customerId);
    }

    private static JwtAuthenticationToken tokenWithCustomerId(Object customerId) {
        Jwt.Builder builder = Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject("someone@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("role", "USER");
        if (customerId != null) {
            builder.claim("customerId", customerId);
        }
        return new JwtAuthenticationToken(builder.build(), List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}
