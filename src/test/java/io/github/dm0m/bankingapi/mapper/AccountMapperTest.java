package io.github.dm0m.bankingapi.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.dm0m.bankingapi.dto.AccountResponse;
import io.github.dm0m.bankingapi.dto.OpenAccountRequest;
import io.github.dm0m.bankingapi.model.Account;
import io.github.dm0m.bankingapi.model.Customer;
import io.github.dm0m.bankingapi.model.enums.AccountCurrency;
import io.github.dm0m.bankingapi.model.enums.AccountStatus;
import io.github.dm0m.bankingapi.model.enums.AccountType;

class AccountMapperTest {

    private final AccountMapper mapper = new AccountMapper();

    private final OpenAccountRequest request = new OpenAccountRequest(AccountType.SAVING, AccountCurrency.USD);

    private Account accountOfCustomer(Long customerId) {
        Customer customer = new Customer();
        ReflectionTestUtils.setField(customer, "id", customerId);
        Account account = mapper.toEntity(request);
        account.setCustomer(customer);
        account.setAccountNumber("FR7630006000011234567890189");
        account.setBalance(new BigDecimal("25.5000"));
        account.setAccountStatus(AccountStatus.ACTIVE);
        return account;
    }

    @Test
    void toEntity_copiesTypeAndCurrency() {
        Account account = mapper.toEntity(request);

        assertThat(account.getAccountType()).isEqualTo(AccountType.SAVING);
        assertThat(account.getCurrency()).isEqualTo(AccountCurrency.USD);
    }

    @Test
    void toEntity_leavesFieldsDecidedByTheServiceUnset() {
        Account account = mapper.toEntity(request);

        assertThat(account.getId()).isNull();
        assertThat(account.getCustomer()).isNull();
        assertThat(account.getAccountNumber()).isNull();
        assertThat(account.getBalance()).isNull();
        assertThat(account.getAccountStatus()).isNull();
    }

    @Test
    void toResponse_copiesAllFields() {
        Account account = accountOfCustomer(7L);
        // id and audit dates have no setters (the database and auditing fill them), so set them directly
        Instant created = Instant.parse("2026-11-01T10:00:00Z");
        Instant updated = Instant.parse("2026-11-02T10:00:00Z");
        ReflectionTestUtils.setField(account, "id", 42L);
        ReflectionTestUtils.setField(account, "createdAt", created);
        ReflectionTestUtils.setField(account, "updatedAt", updated);

        AccountResponse response = mapper.toResponse(account);

        assertThat(response).isEqualTo(new AccountResponse(
                42L, "FR7630006000011234567890189", 7L, new BigDecimal("25.5000"),
                AccountType.SAVING, AccountCurrency.USD, AccountStatus.ACTIVE, created, updated));
    }

    @Test
    void toResponse_mapsCustomerId() {
        AccountResponse response = mapper.toResponse(accountOfCustomer(7L));

        assertThat(response.customerId()).isEqualTo(7L);
    }
}
