package io.github.dm0m.bankingapi.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import io.github.dm0m.bankingapi.TestAccounts;
import io.github.dm0m.bankingapi.TestcontainersConfiguration;
import io.github.dm0m.bankingapi.account.AccountService;
import io.github.dm0m.bankingapi.customer.CustomerService;
import io.github.dm0m.bankingapi.transfer.TransferRequest;
import io.github.dm0m.bankingapi.transfer.TransferService;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransferAuditListenerIntegrationTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    private TransferService transferService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private KafkaTemplate<Object, Object> kafkaTemplate;
    @Autowired
    private ConsumerFactory<Object, Object> consumerFactory;
    @Value("${app.kafka.topics.transfer-completed}")
    private String topic;

    @Test
    void committedTransfer_isAuditedOnce() {
        Long from = TestAccounts.openWithBalance(customerService, accountService, "100");
        Long to = TestAccounts.openWithBalance(customerService, accountService, "0");
        Long transferId = transferService.transfer(new TransferRequest(from, to, new BigDecimal("30.5"))).id();
        await().atMost(TIMEOUT).until(() -> auditRowsForTransfer(transferId) == 1);
        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT from_account_id, to_account_id, amount, currency, kafka_topic FROM transfer_audit_logs WHERE transfer_id = ?",
            transferId);
        assertThat(row.get("from_account_id")).isEqualTo(from);
        assertThat(row.get("to_account_id")).isEqualTo(to);
        assertThat((BigDecimal) row.get("amount")).isEqualByComparingTo("30.5");
        assertThat(row.get("currency")).isEqualTo("EUR");
        assertThat(row.get("kafka_topic")).isEqualTo(topic);
    }

    @Test
    void rolledBackTransfer_isNeverAudited() {
        Long from = TestAccounts.openWithBalance(customerService, accountService, "100");
        Long to = TestAccounts.openWithBalance(customerService, accountService, "999999999999999.0000");
        assertThatThrownBy(() -> transferService.transfer(new TransferRequest(from, to, new BigDecimal("30"))))
                .isInstanceOf(DataIntegrityViolationException.class);
        Long marker = transferService.transfer(new TransferRequest(from, TestAccounts.openWithBalance(customerService, accountService, "0"), BigDecimal.ONE)).id();
        await().atMost(TIMEOUT).until(() -> auditRowsForTransfer(marker) == 1);
        assertThat(auditRowsFromAccount(from)).isEqualTo(1);
    }

    @Test
    void sameEventDeliveredTwice_isAuditedOnce() {
        UUID eventId = UUID.randomUUID();
        long transferId = 900_000_000L + (eventId.getLeastSignificantBits() & 0xFFFFFF);
        String json = eventJson(eventId, transferId);
        kafkaTemplate.send(topic, String.valueOf(transferId), json);
        kafkaTemplate.send(topic, String.valueOf(transferId), json);
        long markerId = transferId + 1;
        kafkaTemplate.send(topic, String.valueOf(transferId), eventJson(UUID.randomUUID(), markerId));
        await().atMost(TIMEOUT).until(() -> auditRowsForTransfer(markerId) == 1);
        assertThat(auditRowsForTransfer(transferId)).isEqualTo(1);
    }

    @Test
    void malformedMessage_goesToTheDeadLetterTopic() {
        String garbage = "not json " + UUID.randomUUID();

        kafkaTemplate.send(topic, "poison", garbage);

        assertArrivesOnDeadLetterTopic(garbage);
    }

    // valid JSON that breaks the contract: no eventId, an amount that isn't a number, an unknown currency
    @ParameterizedTest
    @ValueSource(strings = {
            "{\"transferId\": 1, \"fromAccountId\": 1, \"toAccountId\": 2, \"amount\": \"1\", \"currency\": \"EUR\", \"occurredAt\": \"2026-11-18T10:00:00Z\", \"marker\": \"%s\"}",
            "{\"eventId\": \"%s\", \"transferId\": 1, \"fromAccountId\": 1, \"toAccountId\": 2, \"amount\": \"abc\", \"currency\": \"EUR\", \"occurredAt\": \"2026-11-18T10:00:00Z\"}",
            "{\"eventId\": \"%s\", \"transferId\": 1, \"fromAccountId\": 1, \"toAccountId\": 2, \"amount\": \"1\", \"currency\": \"GBP\", \"occurredAt\": \"2026-11-18T10:00:00Z\"}"
    })
    void messageBreakingTheContract_goesToTheDeadLetterTopicAndIsNotAudited(String template) {
        String id = UUID.randomUUID().toString();
        String message = template.formatted(id);

        kafkaTemplate.send(topic, "invalid", message);

        assertArrivesOnDeadLetterTopic(message);
        assertThat(auditRowsWithPayloadContaining(id)).isZero();
    }

    private void assertArrivesOnDeadLetterTopic(String value) {
        try (Consumer<Object, Object> dlt = deadLetterConsumer()) {
            dlt.subscribe(List.of(topic + "-dlt"));
            await().atMost(TIMEOUT).until(() -> {
                for (ConsumerRecord<Object, Object> record : dlt.poll(Duration.ofMillis(500))) {
                    if (value.equals(record.value())) {
                        return true;
                    }
                }
                return false;
            });
        }
    }

    private int auditRowsWithPayloadContaining(String text) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer_audit_logs WHERE raw_payload::text LIKE ?", Integer.class, "%" + text + "%");
        return count != null ? count : 0;
    }

    // account ids no real account gets, so these rows never count towards a real account's audit rows
    private String eventJson(UUID eventId, long transferId) {
        return """
                {"eventId": "%s", "transferId": %d, "fromAccountId": 900000001, "toAccountId": 900000002,
                 "amount": "12.50", "currency": "EUR", "occurredAt": "2026-11-18T10:00:00Z"}
                """.formatted(eventId, transferId);
    }

    private Consumer<Object, Object> deadLetterConsumer() {
        Properties overrides = new Properties();
        overrides.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        return consumerFactory.createConsumer("dlt-test-" + UUID.randomUUID(), null, null, overrides);
    }

    private int auditRowsForTransfer(Long transferId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer_audit_logs WHERE transfer_id = ?", Integer.class, transferId);
        return count != null ? count : 0;
    }

    private int auditRowsFromAccount(Long accountId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfer_audit_logs WHERE from_account_id = ?", Integer.class, accountId);
        return count != null ? count : 0;
    }
}
