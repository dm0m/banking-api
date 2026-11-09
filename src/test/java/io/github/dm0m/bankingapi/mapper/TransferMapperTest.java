package io.github.dm0m.bankingapi.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.dm0m.bankingapi.dto.TransferRequest;
import io.github.dm0m.bankingapi.dto.TransferResponse;
import io.github.dm0m.bankingapi.model.Account;
import io.github.dm0m.bankingapi.model.Transfer;
import io.github.dm0m.bankingapi.model.enums.AccountCurrency;

class TransferMapperTest {

    private final TransferMapper mapper = new TransferMapper();
    private final TransferRequest request = new TransferRequest(1L, 2L, new BigDecimal("50.0000"));

    private Account account(Long id) {
        Account account = new Account();
        ReflectionTestUtils.setField(account, "id", id);
        account.setCurrency(AccountCurrency.EUR);
        return account;
    }

    @Test
    void toEntity_usesGivenAccountsAndAmount() {
        Account from = account(1L);
        Account to = account(2L);
        Transfer transfer = mapper.toEntity(request, from, to);
        assertThat(transfer.getFromAccount()).isSameAs(from);
        assertThat(transfer.getToAccount()).isSameAs(to);
        assertThat(transfer.getAmount()).isEqualByComparingTo("50");
        assertThat(transfer.getId()).isNull();
    }

    @Test
    void toEntity_takesCurrencyFromSourceAccount() {
        Transfer transfer = mapper.toEntity(request, account(1L), account(2L));
        assertThat(transfer.getCurrency()).isEqualTo(AccountCurrency.EUR);
    }

    @Test
    void toResponse_copiesAllFields() {
        Transfer transfer = mapper.toEntity(request, account(1L), account(2L));
        Instant created = Instant.parse("2026-11-08T10:00:00Z");
        ReflectionTestUtils.setField(transfer, "id", 42L);
        ReflectionTestUtils.setField(transfer, "createdAt", created);
        TransferResponse response = mapper.toResponse(transfer);
        assertThat(response).isEqualTo(
                new TransferResponse(42L, 1L, 2L, new BigDecimal("50.0000"), AccountCurrency.EUR, created));
    }
}
