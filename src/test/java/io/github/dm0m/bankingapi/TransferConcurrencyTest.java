package io.github.dm0m.bankingapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import io.github.dm0m.bankingapi.dto.AddressRequest;
import io.github.dm0m.bankingapi.dto.CreateCustomerRequest;
import io.github.dm0m.bankingapi.dto.DepositRequest;
import io.github.dm0m.bankingapi.dto.OpenAccountRequest;
import io.github.dm0m.bankingapi.dto.TransferRequest;
import io.github.dm0m.bankingapi.exception.InsufficientFundsException;
import io.github.dm0m.bankingapi.model.Account;
import io.github.dm0m.bankingapi.model.enums.AccountCurrency;
import io.github.dm0m.bankingapi.model.enums.AccountType;
import io.github.dm0m.bankingapi.repository.AccountRepository;
import io.github.dm0m.bankingapi.service.AccountService;
import io.github.dm0m.bankingapi.service.CustomerService;
import io.github.dm0m.bankingapi.service.TransferService;

// Concurrent transfers through the real TransferService against a real Postgres.
// Not @Transactional: each thread must run its own transaction, or there is nothing to race.
@SpringBootTest
@Testcontainers
class TransferConcurrencyTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    // below the default Hikari pool size (10), so threads wait on row locks, not on connections
    private static final int THREADS = 8;

    @Autowired
    private CustomerService customerService;
    @Autowired
    private AccountService accountService;
    @Autowired
    private TransferService transferService;
    @Autowired
    private AccountRepository accountRepository;
    @Autowired
    private TransactionTemplate tx;

    @Test
    void transfersInBothDirectionsAtOnce_neverDeadlock() throws Exception {
        // A->B locks A then B; B->A would lock B then A without the id ordering, and Postgres would abort one of them
        Long a = createAccountWithBalance("1000");
        Long b = createAccountWithBalance("1000");
        List<Callable<Void>> transfers = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            transfers.add(transfer(a, b, "1"));
            transfers.add(transfer(b, a, "1"));
        }

        List<Throwable> failures = runAtOnce(transfers);

        assertThat(failures).isEmpty();
        assertThat(balanceOf(a)).isEqualByComparingTo("1000");
        assertThat(balanceOf(b)).isEqualByComparingTo("1000");
    }

    @Test
    void manyRandomTransfers_neverCreateOrLoseMoney() throws Exception {
        Long a = createAccountWithBalance("100");
        Long b = createAccountWithBalance("100");
        Random random = new Random(42); // fixed seed: same amounts on every run
        List<Callable<Void>> transfers = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            String amount = String.valueOf(1 + random.nextInt(60));
            transfers.add(i % 2 == 0 ? transfer(a, b, amount) : transfer(b, a, amount));
        }

        List<Throwable> failures = runAtOnce(transfers);

        // a transfer may be refused for lack of funds, but nothing else may go wrong
        assertThat(failures).allMatch(InsufficientFundsException.class::isInstance);
        assertThat(balanceOf(a).add(balanceOf(b))).isEqualByComparingTo("200");
        assertThat(balanceOf(a)).isNotNegative();
        assertThat(balanceOf(b)).isNotNegative();
    }

    @Test
    void concurrentTransfersFromOneAccount_neverOverdraw() throws Exception {
        Long source = createAccountWithBalance("100");
        Long target = createAccountWithBalance("0");
        List<Callable<Void>> transfers = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            transfers.add(transfer(source, target, "30"));
        }

        List<Throwable> failures = runAtOnce(transfers);

        // 100 covers three transfers of 30; the other seven must see the reduced balance and be refused
        assertThat(failures).hasSize(7).allMatch(InsufficientFundsException.class::isInstance);
        assertThat(balanceOf(source)).isEqualByComparingTo("10");
        assertThat(balanceOf(target)).isEqualByComparingTo("90");
    }

    // Bypasses the row lock to check @Version alone: both threads read version N, the first commit
    // bumps it, so the second UPDATE ... WHERE version = N matches no row and is rejected.
    @Test
    void withoutRowLock_versionRejectsTheSecondConcurrentWrite() throws Exception {
        Long accountId = createAccountWithBalance("100");
        CyclicBarrier bothHaveRead = new CyclicBarrier(2); // forces both threads to read the same balance

        List<Throwable> failures = runAtOnce(List.of(
                withdrawWithoutLock(accountId, "30", bothHaveRead),
                withdrawWithoutLock(accountId, "50", bothHaveRead)));

        assertThat(failures).singleElement().isInstanceOf(ObjectOptimisticLockingFailureException.class);
        // only the winner's write is kept: 100 - 30 or 100 - 50, never a silent overwrite
        assertThat(balanceOf(accountId)).isIn(new BigDecimal("70.0000"), new BigDecimal("50.0000"));
    }

    private Callable<Void> transfer(Long from, Long to, String amount) {
        return () -> {
            transferService.transfer(new TransferRequest(from, to, new BigDecimal(amount)));
            return null;
        };
    }

    private Callable<Void> withdrawWithoutLock(Long accountId, String amount, CyclicBarrier barrier) {
        return () -> {
            tx.executeWithoutResult(status -> {
                Account account = accountRepository.findById(accountId).orElseThrow();
                await(barrier);
                account.setBalance(account.getBalance().subtract(new BigDecimal(amount)));
            });
            return null;
        };
    }

    // starts all tasks at the same moment and returns what each failed task threw
    private List<Throwable> runAtOnce(List<Callable<Void>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Void>> futures = new ArrayList<>();
        try (ExecutorService threads = Executors.newFixedThreadPool(THREADS)) {
            for (Callable<Void> task : tasks) {
                futures.add(threads.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<Throwable> failures = new ArrayList<>();
            for (Future<Void> future : futures) {
                try {
                    future.get(30, TimeUnit.SECONDS);
                } catch (ExecutionException e) {
                    failures.add(e.getCause());
                }
            }
            return failures;
        }
    }

    private void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Long createAccountWithBalance(String balance) {
        AddressRequest address = new AddressRequest("12", "Rue de Rivoli", "Paris", "Ile-de-France", "75001", "FR");
        Long customerId = customerService.create(new CreateCustomerRequest(
                "John", "Doe", "+33612345678", UUID.randomUUID() + "@example.com",
                address, LocalDate.of(1990, 5, 15))).id();
        Long accountId = accountService.open(customerId,
                new OpenAccountRequest(AccountType.CHECKING, AccountCurrency.EUR)).id();
        BigDecimal amount = new BigDecimal(balance);
        if (amount.signum() > 0) {
            accountService.deposit(accountId, new DepositRequest(amount));
        }
        return accountId;
    }

    private BigDecimal balanceOf(Long accountId) {
        return accountService.getAccountById(accountId).balance();
    }
}
