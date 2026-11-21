package io.github.dm0m.bankingapi.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends unsent outbox events to Kafka, oldest first, and marks each one sent
 * once Kafka confirms it.
 *
 * <ul>
 * <li>A batch is claimed in a short transaction, sent with no transaction or
 * row lock open, then the results
 * are saved in a second short transaction. A slow Kafka never holds a database
 * connection or lock.</li>
 * <li>The whole batch is handed to the producer before waiting for any
 * confirmation, so it goes out in a few
 * requests instead of one round trip per event.</li>
 * <li>A crash before {@code sent_at} is committed leaves the event unsent; once
 * its claim expires it is sent
 * again: delivery is at least once, and consumers drop duplicates by
 * {@code eventId}.</li>
 * <li>A failed event is retried on the next run, after later events of its
 * batch may already have been sent,
 * so events are not strictly in outbox order. Each transfer has its own key, so
 * no consumer relies on it.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);
    static final Duration CLAIM_DURATION = Duration.ofSeconds(60);
    private final OutboxEventRepository repository;
    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final int batchSize;
    private final Duration sendTimeout;

    public OutboxPublisher(OutboxEventRepository repository, KafkaTemplate<Object, Object> kafkaTemplate,
            TransactionTemplate tx, Clock clock, @Value("${app.outbox.batch-size}") int batchSize,
            @Value("${app.outbox.send-timeout}") Duration sendTimeout) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.tx = tx;
        this.clock = clock;
        this.batchSize = batchSize;
        this.sendTimeout = sendTimeout;
    }

    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms}")
    public void publishPending() {
        int sent;
        do {
            sent = publishBatch();
        } while (sent == batchSize);
    }

    int publishBatch() {
        List<OutboxEvent> batch = claimBatch();
        if (batch.isEmpty()) {
            return 0;
        }
        List<String> errors = send(batch);
        Integer sent;
        try {
            sent = tx.execute(status -> recordResults(batch, errors));
        } catch (ObjectOptimisticLockingFailureException e) {
            logger.warn("results of {} outbox events not saved: another publisher claimed one of them meanwhile",
                    batch.size());
            return 0;
        }
        return sent != null ? sent : 0;
    }

    private List<OutboxEvent> claimBatch() {
        List<OutboxEvent> batch = tx.execute(status -> {
            Instant now = Instant.now(clock);
            List<OutboxEvent> claimable = repository.lockClaimableBatch(now, batchSize);
            claimable.forEach(event -> event.claim(now.plus(CLAIM_DURATION)));
            return claimable;
        });
        return batch != null ? batch : List.of();
    }

    private List<String> send(List<OutboxEvent> batch) {
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (OutboxEvent event : batch) {
            try {
                futures.add(kafkaTemplate.send(event.getTopic(), event.getMessageKey(), event.getPayload()));
            } catch (RuntimeException e) {
                futures.add(CompletableFuture.failedFuture(e));
                break;
            }
        }
        long deadline = System.nanoTime() + sendTimeout.toNanos();
        List<String> errors = new ArrayList<>();
        for (CompletableFuture<?> future : futures) {
            errors.add(await(future, deadline));
        }
        return errors;
    }

    private static String await(CompletableFuture<?> future, long deadline) {
        try {
            future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "interrupted while sending";
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            Throwable cause = e instanceof ExecutionException && e.getCause() != null ? e.getCause() : e;
            return cause.getClass().getSimpleName() + ": " + cause.getMessage();
        }
    }

    private int recordResults(List<OutboxEvent> batch, List<String> errors) {
        Map<Long, OutboxEvent> loaded = repository
            .findAllById(batch.stream().map(OutboxEvent::getId).toList())
            .stream()
            .collect(Collectors.toMap(OutboxEvent::getId, Function.identity()));
        Instant now = Instant.now(clock);
        int sent = 0;
        int failed = 0;
        String firstError = null;
        for (int i = 0; i < batch.size(); i++) {
            OutboxEvent claimed = batch.get(i);
            OutboxEvent event = loaded.get(claimed.getId());
            if (event == null || event.isSent() || event.getVersion() != claimed.getVersion()) {
                continue;
            }
            if (i >= errors.size()) {
                event.release();
            } else if (errors.get(i) == null) {
                event.markSent(now);
                sent++;
            } else {
                event.recordFailure(now, errors.get(i));
                failed++;
                firstError = firstError != null ? firstError : errors.get(i);
            }
        }
        if (failed > 0) {
            logger.warn("{} of {} outbox events not sent, will retry: {}", failed, batch.size(), firstError);
        }
        return sent;
    }
}
