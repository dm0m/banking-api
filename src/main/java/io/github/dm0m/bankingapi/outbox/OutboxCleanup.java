package io.github.dm0m.bankingapi.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OutboxCleanup {

    private static final Logger logger = LoggerFactory.getLogger(OutboxCleanup.class);
    private final OutboxEventRepository repository;
    private final Clock clock;
    private final Duration retention;

    public OutboxCleanup(
            OutboxEventRepository repository, 
            Clock clock,
            @Value("${app.outbox.retention}") Duration retention) {
        this.repository = repository;
        this.clock = clock;
        this.retention = retention;
    }

    @Scheduled(cron = "${app.outbox.cleanup-cron}")
    @Transactional
    public int deleteOldSentEvents() {
        Instant cutoff = Instant.now(clock).minus(retention);
        int deleted = repository.deleteSentBefore(cutoff);
        if (deleted > 0) logger.info("deleted {} outbox events sent more than {} ago", deleted, retention.toDays() + "d");
        return deleted;
    }
}
