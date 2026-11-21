package io.github.dm0m.bankingapi.audit;

import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Writes one audit row per transfer.completed event. Kafka delivers at least once,
 * so the same event can arrive twice: the unique event_id makes the second delivery a no-op. Any
 * other failure is thrown, so AuditKafkaConfig's error handler retries it and then moves it to the
 * dead-letter topic; returning normally would commit the offset and lose the event.
 */
@Component
public class TransferAuditListener {

    private static final Logger logger = LoggerFactory.getLogger(TransferAuditListener.class);
    private final TransferAuditRepository transferAuditRepository;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public TransferAuditListener(TransferAuditRepository transferAuditRepository, Clock clock,
            ObjectMapper objectMapper) {
        this.transferAuditRepository = transferAuditRepository;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "${app.kafka.topics.transfer-completed}", groupId = "audit")
    public void onTransferCompleted(
            @Payload String rawMessage, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition, @Header(KafkaHeaders.OFFSET) long offset) throws JsonProcessingException {
        
        TransferCompletedMessage message = objectMapper.readValue(rawMessage, TransferCompletedMessage.class);
        logger.info("transfer event {} received from {}-{} at offset {}", message.eventId(), topic, partition, offset);
        if (transferAuditRepository.existsByEventId(message.eventId())) {
            logger.info("transfer event {} already audited, skipping", message.eventId());
            return;
        }
        TransferAuditLog auditLog = new TransferAuditLog(
            message.eventId(), message.transferId(), message.fromAccountId(),
            message.toAccountId(), message.amount(), message.currency(),
            message.occurredAt(), Instant.now(clock), topic, partition,
            offset, rawMessage
        );
        try {
            Long auditId = transferAuditRepository.saveAndFlush(auditLog).getId();
            logger.info("audit record {} saved for transfer {}", auditId, message.transferId());
        } catch (DataIntegrityViolationException ex) {
            if (!transferAuditRepository.existsByEventId(message.eventId())) {
                throw ex;
            }
            logger.info("transfer event {} already audited, skipping", message.eventId());
        }
    }
}
