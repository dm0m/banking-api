package io.github.dm0m.bankingapi.transfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.IllegalTransactionStateException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.dm0m.bankingapi.TestAccounts;
import io.github.dm0m.bankingapi.TestcontainersConfiguration;
import io.github.dm0m.bankingapi.account.AccountService;
import io.github.dm0m.bankingapi.customer.CustomerService;
import io.github.dm0m.bankingapi.outbox.OutboxWriter;

// What the outbox guarantees, against the real database: a transfer and its event commit together or not at all.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransferOutboxIntegrationTest {

    @Autowired
    private TransferService transferService;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private OutboxWriter outboxWriter;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void committedTransfer_leavesExactlyOneOutboxRowDescribingIt() throws Exception {
        Long from = TestAccounts.openWithBalance(customerService, accountService, "100");
        Long to = TestAccounts.openWithBalance(customerService, accountService, "0");
        Long transferId = transferService.transfer(new TransferRequest(from, to, new BigDecimal("30.5"))).id();
        assertThat(outboxRowsFor(transferId)).isEqualTo(1);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT event_id, topic, payload::text AS payload FROM outbox_events WHERE message_key = ?",
                transferId.toString());
        assertThat(row.get("topic")).isEqualTo("transfer.completed");
        JsonNode payload = objectMapper.readTree((String) row.get("payload"));
        assertThat(payload.get("eventId").asText()).isEqualTo(row.get("event_id").toString());
        assertThat(payload.get("transferId").asLong()).isEqualTo(transferId);
        assertThat(payload.get("fromAccountId").asLong()).isEqualTo(from);
        assertThat(payload.get("toAccountId").asLong()).isEqualTo(to);
        assertThat(payload.get("amount").asText()).isEqualTo("30.5000"); // stored scale
        assertThat(payload.get("currency").asText()).isEqualTo("EUR");
        assertThat(payload.get("occurredAt").isTextual()).isTrue();
    }

    @Test
    void rolledBackTransfer_leavesNoOutboxRow() {
        Long from = TestAccounts.openWithBalance(customerService, accountService, "100");
        Long to = TestAccounts.openWithBalance(customerService, accountService, "999999999999999.0000");
        long rowsBefore = outboxRowCount();
        assertThatThrownBy(() -> transferService.transfer(new TransferRequest(from, to, new BigDecimal("30"))))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(outboxRowCount()).isEqualTo(rowsBefore);
    }

    @Test
    void writerOutsideATransaction_refusesInsteadOfCommittingTheEventOnItsOwn() {
        UUID eventId = UUID.randomUUID();
        assertThatThrownBy(() -> outboxWriter.append("transfer.completed", "1", eventId, Map.of("eventId", eventId)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_id = ?", Long.class, eventId)).isZero();
    }

    private long outboxRowsFor(Long transferId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE message_key = ?", Long.class, transferId.toString());
        return count != null ? count : 0;
    }

    private long outboxRowCount() {
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Long.class);
        return count != null ? count : 0;
    }
}
