package io.github.dm0m.bankingapi.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.dm0m.bankingapi.model.User;
import io.github.dm0m.bankingapi.model.enums.Role;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    Optional<User> findByCustomerId(Long customerId);
    boolean existsByRole(Role role);
}
