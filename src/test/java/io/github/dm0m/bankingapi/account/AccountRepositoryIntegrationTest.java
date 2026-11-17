package io.github.dm0m.bankingapi.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.dm0m.bankingapi.TestAccounts;
import io.github.dm0m.bankingapi.TestcontainersConfiguration;
import io.github.dm0m.bankingapi.customer.CustomerService;

// Repository queries, locks and constraints against the real Postgres schema built by Flyway.
// @SpringBootTest rather than @DataJpaTest so it shares the cached context (and container) with the
// other integration tests. Not @Transactional: the lock test needs two separate transactions.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AccountRepositoryIntegrationTest {

    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private CustomerService customerService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private TransactionTemplate tx;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void findForUpdateById_holdsARowLockUntilCommit() throws Exception {
        Long accountId = createAccount();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            accountRepository.findForUpdateById(accountId).orElseThrow();
            locked.countDown();
            awaitQuietly(release);
        }));
        try {
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

            // a second locker gives up after 200 ms instead of waiting for the holder's commit
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
                jdbcTemplate.execute("SET LOCAL lock_timeout = '200ms'");
                accountRepository.findForUpdateById(accountId);
            })).isInstanceOf(ConcurrencyFailureException.class);
        } finally {
            release.countDown();
            holder.get(5, TimeUnit.SECONDS);
        }

        // once the holder committed, the row can be locked again
        tx.executeWithoutResult(status -> assertThat(accountRepository.findForUpdateById(accountId)).isPresent());
    }

    @Test
    void findForUpdateById_doesNotBlockPlainReads() throws Exception {
        Long accountId = createAccount();
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);

        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> tx.executeWithoutResult(status -> {
            accountRepository.findForUpdateById(accountId).orElseThrow();
            locked.countDown();
            awaitQuietly(release);
        }));
        try {
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();

            // GET /accounts/{id} must not hang behind a running transfer
            Boolean found = tx.execute(status -> {
                jdbcTemplate.execute("SET LOCAL lock_timeout = '200ms'");
                return accountRepository.findById(accountId).isPresent();
            });
            assertThat(found).isTrue();
        } finally {
            release.countDown();
            holder.get(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void version_isBumpedOnEveryUpdate() {
        Long accountId = createAccount();
        Long before = accountRepository.findById(accountId).orElseThrow().getVersion();

        accountService.deposit(accountId, new DepositRequest(BigDecimal.TEN));

        assertThat(accountRepository.findById(accountId).orElseThrow().getVersion()).isEqualTo(before + 1);
    }

    @Test
    void existsByIdAndCustomerId_matchesOnlyTheOwner() {
        Long accountId = createAccount();
        Long ownerId = accountService.getAccountById(accountId).customerId();
        Long otherCustomerId = accountService.getAccountById(createAccount()).customerId();

        assertThat(accountRepository.existsByIdAndCustomerId(accountId, ownerId)).isTrue();
        assertThat(accountRepository.existsByIdAndCustomerId(accountId, otherCustomerId)).isFalse();
        assertThat(accountRepository.existsByIdAndCustomerId(999_999L, ownerId)).isFalse();
    }

    @Test
    void accountNumber_isUniqueInTheDatabase() {
        Long first = createAccount();
        Long second = createAccount();
        String takenNumber = accountService.getAccountById(first).accountNumber();

        // the service checks before inserting; the UNIQUE constraint catches whatever slips past that check
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE accounts SET account_number = ? WHERE id = ?", takenNumber, second))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void unknownStatus_isRejectedByTheDatabase() {
        Long accountId = createAccount();

        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE accounts SET account_status = 'FROZEN' WHERE id = ?", accountId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Long createAccount() {
        return TestAccounts.openWithBalance(customerService, accountService, "0");
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test never released the lock");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
