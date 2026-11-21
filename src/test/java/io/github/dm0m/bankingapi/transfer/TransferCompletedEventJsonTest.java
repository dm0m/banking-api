package io.github.dm0m.bankingapi.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.dm0m.bankingapi.account.AccountCurrency;
import io.github.dm0m.bankingapi.audit.TransferCompletedMessage;

// The JSON on the transfer.completed topic is a contract with every consumer. This pins its shape, serialized the
// way OutboxWriter does it (Spring's ObjectMapper; the outbox stores it and the publisher sends it unchanged),
// so renaming a field or changing a type fails here instead of silently breaking a consumer.
class TransferCompletedEventJsonTest {

    private final UUID eventId = UUID.randomUUID();
    private final TransferCompletedEvent event = new TransferCompletedEvent(
            eventId, 42L, 1L, 2L, "30.50", AccountCurrency.EUR, "2026-11-18T10:00:00Z");

    @Test
    void jsonHasExactlyTheContractFields() throws Exception {
        JsonNode json = new ObjectMapper().readTree(serialize(event));
        assertThat(json.properties()).extracting(Map.Entry::getKey).containsExactlyInAnyOrder(
                "eventId", "transferId", "fromAccountId", "toAccountId", "amount", "currency", "occurredAt");
        assertThat(json.get("eventId").asText()).isEqualTo(eventId.toString());
        assertThat(json.get("transferId").asLong()).isEqualTo(42L);
        assertThat(json.get("amount").isTextual()).isTrue();
        assertThat(json.get("amount").asText()).isEqualTo("30.50");
        assertThat(json.get("currency").asText()).isEqualTo("EUR");
        assertThat(json.get("occurredAt").isTextual()).isTrue();
        assertThat(json.get("occurredAt").asText()).isEqualTo("2026-11-18T10:00:00Z");
    }

    @Test
    void auditConsumerReadsBackWhatTheProducerWrites() throws Exception {
        TransferCompletedMessage message = new ObjectMapper().findAndRegisterModules()
                .readValue(serialize(event), TransferCompletedMessage.class);

        assertThat(message.eventId()).isEqualTo(eventId);
        assertThat(message.transferId()).isEqualTo(42L);
        assertThat(message.fromAccountId()).isEqualTo(1L);
        assertThat(message.toAccountId()).isEqualTo(2L);
        assertThat(message.amount()).isEqualTo(new BigDecimal("30.50"));
        assertThat(message.currency()).isEqualTo(AccountCurrency.EUR);
        assertThat(message.occurredAt()).isEqualTo(Instant.parse("2026-11-18T10:00:00Z"));
    }

    private static String serialize(TransferCompletedEvent event) throws Exception {
        return Jackson2ObjectMapperBuilder.json().build().writeValueAsString(event);
    }
}
