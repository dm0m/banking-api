package io.github.dm0m.bankingapi.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.apache.kafka.common.errors.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");
    private static final int BATCH_SIZE = 2;
    // short, so the timeout tests don't wait 15 s; every other send completes immediately
    private static final Duration SEND_TIMEOUT = Duration.ofMillis(200);

    @Mock
    private OutboxEventRepository repository;
    @Mock
    private KafkaTemplate<Object, Object> kafkaTemplate;
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
        publisher = new OutboxPublisher(repository, kafkaTemplate, tx, Clock.fixed(NOW, ZoneOffset.UTC), BATCH_SIZE,
                SEND_TIMEOUT);
    }

    @Test
    void sendsEachEventAsItsRawPayloadOnItsTopicAndKey_thenMarksItSent() {
        OutboxEvent event = event("42");
        batchIs(event);
        when(kafkaTemplate.send("transfer.completed", "42", event.getPayload())).thenReturn(sent());
        int count = publisher.publishBatch();
        assertThat(count).isEqualTo(1);
        assertThat(event.isSent()).isTrue();
        assertThat(event.getSentAt()).isEqualTo(NOW);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getClaimedUntil()).isNull();
    }

    @Test
    void eventsAreClaimedBeforeTheyAreSent() {
        OutboxEvent event = event("1");
        batchIs(event);
        List<Instant> claimWhileSending = new ArrayList<>();
        when(kafkaTemplate.send(any(), any(), any())).thenAnswer(invocation -> {
            claimWhileSending.add(event.getClaimedUntil());
            return sent();
        });
        publisher.publishBatch();
        assertThat(claimWhileSending).containsExactly(NOW.plus(OutboxPublisher.CLAIM_DURATION));
    }

    @Test
    void wholeBatchIsHandedToTheProducerBeforeAnyConfirmation() {
        OutboxEvent first = event("1");
        OutboxEvent second = event("2");
        batchIs(first, second);
        CompletableFuture<SendResult<Object, Object>> firstResult = new CompletableFuture<>();
        when(kafkaTemplate.send("transfer.completed", "1", first.getPayload())).thenReturn(firstResult);
        when(kafkaTemplate.send("transfer.completed", "2", second.getPayload())).thenAnswer(invocation -> {
            firstResult.complete(null);
            return sent();
        });
        assertThat(publisher.publishBatch()).isEqualTo(2);
    }

    @Test
    void failedSend_isRecordedAndTheRestOfTheBatchIsStillMarkedSent() {
        OutboxEvent first = event("1");
        OutboxEvent second = event("2");
        batchIs(first, second);
        when(kafkaTemplate.send("transfer.completed", "1", first.getPayload()))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("broker unreachable")));
        when(kafkaTemplate.send("transfer.completed", "2", second.getPayload())).thenReturn(sent());
        int count = publisher.publishBatch();
        assertThat(count).isEqualTo(1);
        assertThat(first.isSent()).isFalse();
        assertThat(first.getAttempts()).isEqualTo(1);
        assertThat(first.getLastError()).isEqualTo("TimeoutException: broker unreachable");
        assertThat(first.getClaimedUntil()).isNull();
        assertThat(second.isSent()).isTrue();
    }

    @Test
    void sendThatThrowsImmediately_isRecorded_andLaterEventsAreReleasedUntried() {
        OutboxEvent first = event("1");
        OutboxEvent second = event("2");
        batchIs(first, second);
        when(kafkaTemplate.send(any(), any(), any()))
                .thenThrow(new org.springframework.kafka.KafkaException("Send failed"));
        assertThat(publisher.publishBatch()).isZero();
        assertThat(first.isSent()).isFalse();
        assertThat(first.getLastError()).startsWith("KafkaException: Send failed");
        verify(kafkaTemplate, never()).send(eq("transfer.completed"), eq("2"), any());
        assertThat(second.getAttempts()).isZero();
        assertThat(second.getClaimedUntil()).isNull();
    }

    @Test
    void sendsThatNeverComplete_timeOutTogether_andAreRecordedAsFailures() {
        OutboxEvent first = event("1");
        OutboxEvent second = event("2");
        batchIs(first, second);
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(new CompletableFuture<>());
        long start = System.nanoTime();
        int count = publisher.publishBatch();
        Duration took = Duration.ofNanos(System.nanoTime() - start);
        assertThat(count).isZero();
        assertThat(List.of(first, second)).allSatisfy(event -> {
            assertThat(event.isSent()).isFalse();
            assertThat(event.getAttempts()).isEqualTo(1);
            assertThat(event.getLastError()).startsWith("TimeoutException");
            assertThat(event.getClaimedUntil()).isNull();
        });
        assertThat(took).isGreaterThanOrEqualTo(SEND_TIMEOUT).isLessThan(SEND_TIMEOUT.multipliedBy(2));
    }

    @Test
    void interruptedWhileWaiting_recordsTheEventAsFailed_andKeepsTheInterruptFlag() {
        OutboxEvent event = event("1");
        batchIs(event);
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(new CompletableFuture<>());
        Thread.currentThread().interrupt();
        try {
            assertThat(publisher.publishBatch()).isZero();
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        assertThat(event.isSent()).isFalse();
        assertThat(event.getLastError()).isEqualTo("interrupted while sending");
    }

    @Test
    void eventDeletedBeforeTheResultsAreSaved_isSkipped_andTheRestIsStillMarkedSent() {
        OutboxEvent deleted = event("1");
        OutboxEvent kept = event("2");
        when(repository.lockClaimableBatch(NOW, BATCH_SIZE)).thenReturn(List.of(deleted, kept));
        when(repository.findAllById(List.of(1L, 2L))).thenReturn(List.of(kept));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(sent());
        assertThat(publisher.publishBatch()).isEqualTo(1);
        assertThat(kept.isSent()).isTrue();
    }

    @Test
    void eventAnotherPublisherSentAfterOurClaimExpired_isLeftAsThatPublisherSavedIt() {
        OutboxEvent ours = event("1");
        OutboxEvent alreadySent = event("1");
        Instant sentByOther = NOW.minusSeconds(5);
        alreadySent.markSent(sentByOther);
        when(repository.lockClaimableBatch(NOW, BATCH_SIZE)).thenReturn(List.of(ours));
        when(repository.findAllById(List.of(1L))).thenReturn(List.of(alreadySent));
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("broker unreachable")));
        assertThat(publisher.publishBatch()).isZero();
        assertThat(alreadySent.getSentAt()).isEqualTo(sentByOther);
        assertThat(alreadySent.getAttempts()).isEqualTo(1);
        assertThat(alreadySent.getLastError()).isNull();
    }

    @Test
    void eventAnotherPublisherClaimedAgainAfterOurClaimExpired_isLeftToThatPublisher() {
        OutboxEvent ours = event("1");
        OutboxEvent reclaimed = event("1");
        Instant theirClaim = NOW.plusSeconds(30);
        reclaimed.claim(theirClaim);
        ReflectionTestUtils.setField(reclaimed, "version", ours.getVersion() + 1);
        when(repository.lockClaimableBatch(NOW, BATCH_SIZE)).thenReturn(List.of(ours));
        when(repository.findAllById(List.of(1L))).thenReturn(List.of(reclaimed));
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new TimeoutException("broker unreachable")));
        assertThat(publisher.publishBatch()).isZero();
        assertThat(reclaimed.getClaimedUntil()).isEqualTo(theirClaim);
        assertThat(reclaimed.getAttempts()).isZero();
        assertThat(reclaimed.getLastError()).isNull();
    }

    @Test
    void anotherPublisherClaimsDuringOurCommit_resultsAreDropped_andTheEventsWaitForTheClaimToExpire() {
        PlatformTransactionManager manager = mock(PlatformTransactionManager.class);
        doNothing().doThrow(new ObjectOptimisticLockingFailureException(OutboxEvent.class, 1L))
                .when(manager).commit(any());
        OutboxPublisher racing = new OutboxPublisher(repository, kafkaTemplate, new TransactionTemplate(manager),
                Clock.fixed(NOW, ZoneOffset.UTC), BATCH_SIZE, SEND_TIMEOUT);
        batchIs(event("1"));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(sent());
        assertThat(racing.publishBatch()).isZero();
    }

    @Test
    void publishPending_keepsGoingWhileBatchesAreFull_thenStops() {
        OutboxEvent a = event("1");
        OutboxEvent b = event("2");
        OutboxEvent c = event("3");
        when(repository.lockClaimableBatch(NOW, BATCH_SIZE)).thenReturn(List.of(a, b), List.of(c));
        when(repository.findAllById(any())).thenReturn(List.of(a, b), List.of(c));
        when(kafkaTemplate.send(any(), any(), any())).thenReturn(sent());
        publisher.publishPending();
        verify(repository, times(2)).lockClaimableBatch(any(), anyInt());
        assertThat(List.of(a, b, c)).allMatch(OutboxEvent::isSent);
    }

    @Test
    void publishPending_emptyOutbox_doesNothing() {
        when(repository.lockClaimableBatch(NOW, BATCH_SIZE)).thenReturn(List.of());
        publisher.publishPending();
        verify(repository, times(1)).lockClaimableBatch(any(), anyInt());
        verify(repository, never()).findAllById(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    private void batchIs(OutboxEvent... events) {
        when(repository.lockClaimableBatch(NOW, BATCH_SIZE)).thenReturn(List.of(events));
        when(repository.findAllById(any())).thenReturn(List.of(events));
    }

    private static OutboxEvent event(String key) {
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = new OutboxEvent(eventId, "transfer.completed", key, "{\"eventId\":\"" + eventId + "\"}", NOW);
        ReflectionTestUtils.setField(event, "id", Long.valueOf(key));
        return event;
    }

    private static CompletableFuture<SendResult<Object, Object>> sent() {
        return CompletableFuture.completedFuture(null);
    }
}
