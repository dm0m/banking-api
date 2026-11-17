package io.github.dm0m.bankingapi.customer;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
    
    boolean existsByEmail(String email);
}
