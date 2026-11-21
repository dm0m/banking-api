package io.github.dm0m.bankingapi.outbox;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(value = """
            SELECT * FROM outbox_events
            WHERE sent_at IS NULL
              AND (claimed_until IS NULL OR claimed_until < :now)
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEvent> lockClaimableBatch(@Param("now") Instant now, @Param("limit") int limit);

    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.sentAt IS NOT NULL AND e.sentAt < :cutoff")
    int deleteSentBefore(@Param("cutoff") Instant cutoff);

    long countBySentAtIsNull();
}
