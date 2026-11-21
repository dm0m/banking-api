package io.github.dm0m.bankingapi.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.dm0m.bankingapi.account.AccountCurrency;

@ExtendWith(MockitoExtension.class)
class TransferAuditListenerTest {

    private static final Instant NOW = Instant.parse("2026-11-18T12:00:00Z");
    private static final String TOPIC = "transfer.completed";

    @Mock
    private TransferAuditRepository repository;
    private TransferAuditListener listener;

    private final UUID eventId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        listener = new TransferAuditListener(repository, Clock.fixed(NOW, ZoneOffset.UTC),
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void newEvent_isSavedWithEveryFieldAndItsKafkaCoordinates() throws Exception {
        when(repository.saveAndFlush(any(TransferAuditLog.class))).thenAnswer(invocation -> {
            TransferAuditLog log = invocation.getArgument(0);
            ReflectionTestUtils.setField(log, "id", 7L);
            return log;
        });
        String json = eventJson("\"12.50\"", "\"EUR\"");
        listener.onTransferCompleted(json, TOPIC, 2, 41L);
        ArgumentCaptor<TransferAuditLog> saved = ArgumentCaptor.forClass(TransferAuditLog.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue())
                .hasFieldOrPropertyWithValue("eventId", eventId)
                .hasFieldOrPropertyWithValue("transferId", 42L)
                .hasFieldOrPropertyWithValue("fromAccountId", 1L)
                .hasFieldOrPropertyWithValue("toAccountId", 2L)
                .hasFieldOrPropertyWithValue("amount", new BigDecimal("12.50"))
                .hasFieldOrPropertyWithValue("currency", AccountCurrency.EUR)
                .hasFieldOrPropertyWithValue("occurredAt", Instant.parse("2026-11-18T10:00:00Z"))
                .hasFieldOrPropertyWithValue("auditedAt", NOW)
                .hasFieldOrPropertyWithValue("kafkaTopic", TOPIC)
                .hasFieldOrPropertyWithValue("kafkaPartition", 2)
                .hasFieldOrPropertyWithValue("kafkaOffset", 41L)
                .hasFieldOrPropertyWithValue("rawPayload", json);
    }

    @Test
    void alreadyAuditedEvent_isSkippedWithoutSaving() throws Exception {
        when(repository.existsByEventId(eventId)).thenReturn(true);
        listener.onTransferCompleted(eventJson("\"12.50\"", "\"EUR\""), TOPIC, 0, 1L);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void concurrentDuplicate_constraintViolationForAnAuditedEvent_isSkipped() {
        when(repository.existsByEventId(eventId)).thenReturn(false, true);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uq_transfer_audit_event_id"));
        assertThatCode(() -> listener.onTransferCompleted(eventJson("\"12.50\"", "\"EUR\""), TOPIC, 0, 1L))
                .doesNotThrowAnyException();
    }

    @Test
    void otherConstraintViolation_isRethrownSoTheErrorHandlerSeesIt() {
        when(repository.existsByEventId(eventId)).thenReturn(false, false);
        DataIntegrityViolationException violation = new DataIntegrityViolationException("uq_transfer_audit_kafka_coords");
        when(repository.saveAndFlush(any())).thenThrow(violation);
        assertThatThrownBy(() -> listener.onTransferCompleted(eventJson("\"12.50\"", "\"EUR\""), TOPIC, 0, 1L))
                .isSameAs(violation);
    }

    @Test
    void notJson_failsAsJsonProcessingException() {
        assertThatThrownBy(() -> listener.onTransferCompleted("not json", TOPIC, 0, 1L))
                .isInstanceOf(JsonProcessingException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"transferId\": 42, \"fromAccountId\": 1, \"toAccountId\": 2, \"amount\": \"1\", \"currency\": \"EUR\", \"occurredAt\": \"2026-11-18T10:00:00Z\"}",
            "{\"eventId\": \"%s\", \"transferId\": 42, \"fromAccountId\": 1, \"toAccountId\": 2, \"amount\": \"abc\", \"currency\": \"EUR\", \"occurredAt\": \"2026-11-18T10:00:00Z\"}",
            "{\"eventId\": \"%s\", \"transferId\": 42, \"fromAccountId\": 1, \"toAccountId\": 2, \"amount\": \"1\", \"currency\": \"GBP\", \"occurredAt\": \"2026-11-18T10:00:00Z\"}",
            "{\"eventId\": \"%s\", \"transferId\": 42, \"fromAccountId\": 1, \"toAccountId\": 2, \"amount\": \"1\", \"currency\": \"EUR\"}"
    })
    void missingOrInvalidField_failsAsJsonProcessingException(String template) {
        String json = template.formatted(eventId);

        assertThatThrownBy(() -> listener.onTransferCompleted(json, TOPIC, 0, 1L))
                .isInstanceOf(JsonProcessingException.class);
        verify(repository, never()).saveAndFlush(any());
    }

    private String eventJson(String amount, String currency) {
        return """
                {"eventId": "%s", "transferId": 42, "fromAccountId": 1, "toAccountId": 2,
                 "amount": %s, "currency": %s, "occurredAt": "2026-11-18T10:00:00Z"}
                """.formatted(eventId, amount, currency);
    }
}
