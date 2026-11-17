package io.github.dm0m.bankingapi.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.dm0m.bankingapi.model.Transfer;

public interface TransferRepository extends JpaRepository<Transfer, Long> {
    Page<Transfer> findByFromAccountIdOrToAccountId(Long fromAccountId, Long toAccountId, Pageable pageable);

    // true if the customer owns the sending or the receiving account
    @Query("""
            select count(t) > 0 from Transfer t
            where t.id = :transferId
              and (t.fromAccount.customer.id = :customerId or t.toAccount.customer.id = :customerId)
            """)
    boolean involvesCustomer(@Param("transferId") Long transferId, @Param("customerId") Long customerId);
}
