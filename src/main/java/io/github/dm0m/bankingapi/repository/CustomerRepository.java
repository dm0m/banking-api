package io.github.dm0m.bankingapi.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.dm0m.bankingapi.model.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    
    boolean existsByEmail(String email);
}
