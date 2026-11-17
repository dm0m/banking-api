package io.github.dm0m.bankingapi.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import io.github.dm0m.bankingapi.common.Emails;

@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrap(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.admin.email}") String adminEmail,
            @Value("${app.admin.password}") String adminPassword) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        createAdminUser();
    }

    private void createAdminUser() {
        if (adminEmail.isBlank() && adminPassword.isBlank()) {
            logger.info("app.admin.email and app.admin.password not set");
            return;
        }
        if (adminEmail.isBlank() || adminPassword.isBlank()) {
            throw new IllegalStateException("app.admin.email or app.admin.password not set");
        }
        if (userRepository.existsByRole(Role.ADMIN)) {
            logger.info("admin user exists, skipping creation");
            return;
        }
        if (adminPassword.length() < 12 || adminPassword.length() > 72) {
            throw new IllegalStateException("app.admin.password must be 12-72 characters");
        }
        if (userRepository.existsByEmail(Emails.normalize(adminEmail))) {
            throw new IllegalStateException("app.admin.email is already used by another login");
        }
        User admin = new User();
        admin.setEmail(adminEmail);
        admin.setRole(Role.ADMIN);
        admin.setPasswordHash(passwordEncoder.encode(adminPassword));
        userRepository.save(admin);
        logger.info("admin user {} created", adminEmail);
    }
}
