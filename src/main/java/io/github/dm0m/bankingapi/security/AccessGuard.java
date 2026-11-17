package io.github.dm0m.bankingapi.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import io.github.dm0m.bankingapi.account.AccountRepository;
import io.github.dm0m.bankingapi.transfer.TransferRepository;

/**
 * Ownership checks for {@code @PreAuthorize}, e.g. {@code @access.ownsAccount(authentication, #id)}.
 * An id that doesn't exist is "not yours" (403, not 404), so callers can't probe which ids exist.
 */
@Component("access")
public class AccessGuard {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;

    public AccessGuard(AccountRepository accountRepository, TransferRepository transferRepository) {
        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
    }

    public boolean ownsAccount(Authentication authentication, Long accountId) {
        Long customerId = customerIdOf(authentication);
        return customerId != null && accountId != null
                && accountRepository.existsByIdAndCustomerId(accountId, customerId);
    }

    // sender or recipient
    public boolean canSeeTransfer(Authentication authentication, Long transferId) {
        Long customerId = customerIdOf(authentication);
        return customerId != null && transferId != null
                && transferRepository.involvesCustomer(transferId, customerId);
    }

    // null for admins (no customer) and for anything that isn't one of our JWTs
    private static Long customerIdOf(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken token
                && token.getToken().getClaim("customerId") instanceof Number id) {
            return id.longValue();
        }
        return null;
    }
}
