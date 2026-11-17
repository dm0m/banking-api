package io.github.dm0m.bankingapi.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
import org.springframework.context.annotation.Import;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.dm0m.bankingapi.TestAccounts;
import io.github.dm0m.bankingapi.TestcontainersConfiguration;
import io.github.dm0m.bankingapi.dto.TransferRequest;
import io.github.dm0m.bankingapi.exception.InsufficientFundsException;
import io.github.dm0m.bankingapi.model.Account;
import io.github.dm0m.bankingapi.repository.AccountRepository;

// Concurrent transfers through the real TransferService against a real Postgres.
// Not @Transactional: each thread must run its own transaction, or there is nothing to race.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransferConcurrencyTest {

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

    /** Wraps a {@link TransferService#transfer} call as a task for {@link #runAtOnce}. */
    private Callable<Void> transfer(Long from, Long to, String amount) {
        return () -> {
            transferService.transfer(new TransferRequest(from, to, new BigDecimal(amount)));
            return null;
        };
    }

    /**
     * Withdraws in its own transaction via {@code findById}, which takes no row lock, so only
     * {@code @Version} guards the write. Waits on {@code barrier} after reading, so all parties
     * read the same version before any of them writes.
     */
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

    /**
     * Runs the tasks on separate threads, released together by a latch to maximise contention.
     *
     * @return the exception thrown by each failed task; empty if all succeeded
     */
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

    /** {@link CyclicBarrier#await} with a 5s timeout; rethrows unchecked so it fits in lambdas. */
    private void await(CyclicBarrier barrier) {
        try {
            barrier.await(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Opens an account for a new customer and funds it with {@code balance}; returns its id. */
    private Long createAccountWithBalance(String balance) {
        return TestAccounts.openWithBalance(customerService, accountService, balance);
    }

    /** Reads the committed balance of the account. */
    private BigDecimal balanceOf(Long accountId) {
        return accountService.getAccountById(accountId).balance();
    }
}
