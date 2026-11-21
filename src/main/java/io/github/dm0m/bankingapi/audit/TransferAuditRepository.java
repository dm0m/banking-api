package io.github.dm0m.bankingapi.audit;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferAuditRepository extends JpaRepository<TransferAuditLog, Long> {

    boolean existsByEventId(UUID eventId);
}
