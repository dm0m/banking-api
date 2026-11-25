package io.github.dm0m.bankingapi.auth;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.dm0m.bankingapi.common.Emails;
import io.github.dm0m.bankingapi.customer.Customer;
import io.github.dm0m.bankingapi.customer.CustomerNotFoundException;
import io.github.dm0m.bankingapi.customer.CustomerRepository;
import io.github.dm0m.bankingapi.customer.EmailAlreadyExistsException;

@Service
public class AuthService {

    private static final String DUMMY_PASSWORD_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final JwtEncoder jwtEncoder;
    private final Duration tokenTtl;
    private final Duration activationCodeTtl;
    private final SecureRandom secureRandom = new SecureRandom();
    private final LoginAttemptService loginAttemptService;

    public AuthService(UserRepository userRepository, CustomerRepository customerRepository,
            PasswordEncoder passwordEncoder, Clock clock, JwtEncoder jwtEncoder,
            @Value("${app.jwt.ttl}") Duration tokenTtl,
            @Value("${app.activation-code.ttl}") Duration activationCodeTtl,
            LoginAttemptService loginAttemptService) {
        this.userRepository = userRepository;
        this.customerRepository = customerRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.jwtEncoder = jwtEncoder;
        this.tokenTtl = tokenTtl;
        this.activationCodeTtl = activationCodeTtl;
        this.loginAttemptService = loginAttemptService;
    }

    /**
     * Creates the customer's pending login if needed and gives it a fresh one-time code,
     * replacing any earlier code that was lost or expired.
     */
    @Transactional
    public ActivationCodeResponse issueActivationCode(Long customerId) {
        Customer customer = customerRepository.findById(customerId).orElseThrow(() -> new CustomerNotFoundException(customerId));
        User user = userRepository.findByCustomerId(customerId).orElse(null);

        if (user != null && user.isActivated()) {
            throw new AlreadyActivatedException(customerId);
        }
        if (user == null) {
            // another login (e.g. an admin) may already use this email
            if (userRepository.existsByEmail(customer.getEmail())) {
                throw new EmailAlreadyExistsException();
            }
            user = new User();
            user.setEmail(customer.getEmail());
            user.setRole(Role.USER);
            user.setCustomer(customer);
        }

        byte[] codeBytes = new byte[16];
        secureRandom.nextBytes(codeBytes);
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(codeBytes);
        Instant expiresAt = clock.instant().plus(activationCodeTtl);
        user.setActivationCodeHash(passwordEncoder.encode(code));
        user.setActivationExpiresAt(expiresAt);
        userRepository.save(user);

        return new ActivationCodeResponse(code, expiresAt);
    }

    /** Sets the password of a pending login*/
    @Transactional
    public void register(RegisterRequest request) {
        User user = userRepository.findByEmail(Emails.normalize(request.email()))
                .orElseThrow(InvalidActivationCodeException::new);
        if (user.isActivated()) {
            throw new InvalidActivationCodeException();
        }
        if (user.getActivationCodeHash() == null
                || !passwordEncoder.matches(request.activationCode(), user.getActivationCodeHash())) {
            throw new InvalidActivationCodeException();
        }
        if (user.getActivationExpiresAt() == null || clock.instant().isAfter(user.getActivationExpiresAt())) {
            throw new InvalidActivationCodeException();
        }
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        // the code is spent: don't keep its hash around
        user.setActivationCodeHash(null);
        user.setActivationExpiresAt(null);
    }

    public TokenResponse login(LoginRequest request) {
        String email = Emails.normalize(request.email());
        Instant lockedUntil = loginAttemptService.getLockedUntil(email);
        if (lockedUntil != null && lockedUntil.isAfter(clock.instant())) {
            throw new LoginLockedException(lockedUntil);
        }
        User user = userRepository.findByEmail(email).orElse(null);
        String passwordHash = user == null || user.getPasswordHash() == null
                ? DUMMY_PASSWORD_HASH
                : user.getPasswordHash();
        // always hash so response time doesn't reveal which emails exist
        boolean passwordMatches = passwordEncoder.matches(request.password(), passwordHash);
        if (user == null || !user.isActivated() || !passwordMatches) {
            loginAttemptService.recordFailure(email);
            throw new InvalidCredentialsException();
        }
        loginAttemptService.clearCount(email);
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, buildClaims(user))).getTokenValue();
        return new TokenResponse(token, "Bearer", tokenTtl.toSeconds());
    }

    private JwtClaimsSet buildClaims(User user) {
        Instant now = clock.instant();
        return JwtClaimsSet.builder()
            .issuer("banking-api")
            .issuedAt(now)
            .expiresAt(now.plus(tokenTtl))
            .subject(user.getId().toString())
            .claim("role", user.getRole().name())
            // admins have no customer
            .claims(c -> {
                if (user.getCustomer() != null) {
                    c.put("customerId", user.getCustomer().getId());
                }
            })
            .build();
    }

}
