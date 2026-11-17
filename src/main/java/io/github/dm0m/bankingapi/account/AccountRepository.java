package io.github.dm0m.bankingapi.account;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import jakarta.persistence.LockModeType;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findByCustomerId(Long customerId);

    boolean existsByAccountNumber(String accountNumber);

    boolean existsByIdAndCustomerId(Long id, Long customerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Account> findForUpdateById(Long id);
}
