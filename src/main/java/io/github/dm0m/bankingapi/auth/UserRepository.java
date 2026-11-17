package io.github.dm0m.bankingapi.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByCustomerId(Long customerId);
    boolean existsByRole(Role role);
}
