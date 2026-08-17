package com.imanirajian.finance.cbs.service;

import com.imanirajian.finance.cbs.domain.Account;
import com.imanirajian.finance.cbs.domain.exception.AccountNotFoundException;
import com.imanirajian.finance.cbs.domain.exception.InsufficientFundsException;
import com.imanirajian.finance.cbs.domain.exception.InvalidAmountException;
import com.imanirajian.finance.cbs.domain.exception.InvalidTransactionException;
import com.imanirajian.finance.cbs.repository.InMemoryAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static ch.qos.logback.core.util.ExecutorServiceUtil.shutdown;
import static org.junit.jupiter.api.Assertions.*;

/**
 * @author Iman Irajian
 * Date: 8/15/2026 1:43 AM
 */

class ConcurrentBalanceServiceTest {

    InMemoryAccountRepository repo;
    BalanceService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryAccountRepository();
        service = new ConcurrentBalanceService(repo, new InMemoryIdempotencyStore());
    }

    @Test
    void credit() {
        repo.save(new Account("A", 1000));
        service.credit("A", 500, "TX1");
        assertEquals(1500, service.getBalance("A"));
    }

    @Test
    void debitCannotGoNegative() {
        repo.save(new Account("A", 1000));
        assertThrows(InsufficientFundsException.class, () -> service.debit("A", 1001, "TX1"));
        assertEquals(1000, service.getBalance("A"));
    }

    @Test
    void duplicateCredit() {
        repo.save(new Account("A", 1000));
        for (int i = 0; i < 3; i++) service.credit("A", 100, "TX1");
        assertEquals(1100, service.getBalance("A"));
    }

    @Test
    void concurrentDuplicateCreditAppliesOnce() throws Exception {
        repo.save(new Account("A", 1000));
        int n = 100;
        var pool = Executors.newFixedThreadPool(32);
        var start = new CountDownLatch(1);
        var fs = new ArrayList<Future<?>>();
        try {
            for (int i = 0; i < n; i++)
                fs.add(pool.submit(() -> {
                    start.await();
                    service.credit("A", 500, "SAME");
                    return null;
                }));
            start.countDown();
            for (var f : fs) f.get(10, TimeUnit.SECONDS);
            assertEquals(1500, service.getBalance("A"));
        } finally {
            pool.shutdown();

            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void thousandConcurrentDebitsNeverGoNegative() throws Exception {
        repo.save(new Account("A", 100000));
        int n = 1000;
        var pool = Executors.newFixedThreadPool(32);
        var start = new CountDownLatch(1);
        var fs = new ArrayList<Future<Boolean>>();
        try {
            for (int i = 0; i < n; i++) {
                int x = i;
                fs.add(pool.submit(() -> {
                    start.await();
                    try {
                        service.debit("A", 100, "D" + x);
                        return true;
                    } catch (InsufficientFundsException e) {
                        return false;
                    }
                }));
            }
            start.countDown();
            long ok = 0;
            for (var f : fs) if (f.get(10, TimeUnit.SECONDS)) ok++;
            assertEquals(1000, ok);
            assertEquals(0, service.getBalance("A"));
        } finally {
            pool.shutdown();

            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void independentAccountsCanRunConcurrently() throws Exception {
        repo.save(new Account("A", 0));
        repo.save(new Account("B", 0));
        int n = 500;
        var pool = Executors.newFixedThreadPool(32);
        var start = new CountDownLatch(1);
        var fs = new ArrayList<Future<?>>();
        try {
            for (int i = 0; i < n; i++) {
                int x = i;
                fs.add(pool.submit(() -> {
                    start.await();
                    service.credit("A", 1, "A" + x);
                    return null;
                }));
                fs.add(pool.submit(() -> {
                    start.await();
                    service.credit("B", 1, "B" + x);
                    return null;
                }));
            }
            start.countDown();
            for (var f : fs) f.get(10, TimeUnit.SECONDS);
            assertEquals(n, service.getBalance("A"));
            assertEquals(n, service.getBalance("B"));
        } finally {
            pool.shutdown();

            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void transferIsCorrectAndAtomic() {
        repo.save(new Account("A", 1000));
        repo.save(new Account("B", 500));
        service.transfer("A", "B", 300, "T1");
        assertEquals(700, service.getBalance("A"));
        assertEquals(800, service.getBalance("B"));
    }

    @Test
    void duplicateTransferAppliesOnce() {
        repo.save(new Account("A", 1000));
        repo.save(new Account("B", 500));
        service.transfer("A", "B", 300, "T1");
        service.transfer("A", "B", 300, "T1");
        assertEquals(700, service.getBalance("A"));
        assertEquals(800, service.getBalance("B"));
    }

    @Test
    void concurrentDuplicateTransferAppliesOnce() throws Exception {
        repo.save(new Account("A", 10000));
        repo.save(new Account("B", 0));
        int n = 100;
        var pool = Executors.newFixedThreadPool(32);
        var start = new CountDownLatch(1);
        var fs = new ArrayList<Future<?>>();
        try {
            for (int i = 0; i < n; i++)
                fs.add(pool.submit(() -> {
                    start.await();
                    service.transfer("A", "B", 100, "T");
                    return null;
                }));
            start.countDown();
            for (var f : fs) f.get(10, TimeUnit.SECONDS);
            assertEquals(9900, service.getBalance("A"));
            assertEquals(100, service.getBalance("B"));
        } finally {
            pool.shutdown();

            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void oppositeDirectionTransfersDoNotDeadlock() throws Exception {
        repo.save(new Account("A", 1_000_000));
        repo.save(new Account("B", 1_000_000));
        int n = 1000;
        var pool = Executors.newFixedThreadPool(32);
        var start = new CountDownLatch(1);
        var fs = new ArrayList<Future<?>>();
        try {
            for (int i = 0; i < n; i++) {
                int x = i;
                fs.add(pool.submit(() -> {
                    start.await();
                    if ((x & 1) == 0) service.transfer("A", "B", 1, "AB" + x);
                    else service.transfer("B", "A", 1, "BA" + x);
                    return null;
                }));
            }
            start.countDown();
            for (var f : fs) f.get(10, TimeUnit.SECONDS);
            assertEquals(1_000_000, service.getBalance("A"));
            assertEquals(1_000_000, service.getBalance("B"));
        } finally {
            pool.shutdown();

            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void failedTransactionIsIdempotent() {
        repo.save(new Account("A", 100));
        assertThrows(InsufficientFundsException.class, () -> service.debit("A", 200, "FAIL"));
        service.credit("A", 200, "CREDIT");
        assertThrows(InsufficientFundsException.class, () -> service.debit("A", 200, "FAIL"));
        assertEquals(300, service.getBalance("A"));
    }

    @Test
    void sameTransactionIdDifferentPayloadIsRejected() {
        repo.save(new Account("A", 1000));
        service.credit("A", 100, "T");
        assertThrows(InvalidTransactionException.class, () -> service.credit("A", 200, "T"));
        assertEquals(1100, service.getBalance("A"));
    }

    @Test
    void sameAccountTransferRejected() {
        repo.save(new Account("A", 1000));
        assertThrows(InvalidTransactionException.class, () -> service.transfer("A", "A", 100, "T"));
        assertEquals(1000, service.getBalance("A"));
    }

    @Test
    void invalidAmountRejected() {
        repo.save(new Account("A", 1000));
        assertThrows(InvalidAmountException.class, () -> service.credit("A", 0, "T"));
    }

    @Test
    void unknownAccountRejected() {
        assertThrows(AccountNotFoundException.class, () -> service.getBalance("X"));
    }

    @Test
    void transferInvariantIsAlwaysPreservedUnderConcurrency()
            throws Exception {

        repo.save(new Account("A", 1_000_000));
        repo.save(new Account("B", 1_000_000));

        long expectedTotal = 2_000_000;

        int operations = 5_000;

        var pool = Executors.newFixedThreadPool(32);
        var start = new CountDownLatch(1);
        var futures = new ArrayList<Future<?>>();

        try {
            for (int i = 0; i < operations; i++) {
                int tx = i;

                futures.add(pool.submit(() -> {
                    start.await();

                    if ((tx & 1) == 0) {
                        service.transfer(
                                "A",
                                "B",
                                10,
                                "AB-" + tx
                        );
                    } else {
                        service.transfer(
                                "B",
                                "A",
                                10,
                                "BA-" + tx
                        );
                    }

                    return null;
                }));
            }

            start.countDown();

            for (Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }

            long total =
                    service.getBalance("A")
                            + service.getBalance("B");

            assertEquals(
                    expectedTotal,
                    total
            );

        } finally {
            shutdown(pool);
        }
    }

    @Test
    void concurrentSameTransactionWithDifferentPayloadIsRejected() throws Exception {
        repo.save(new Account("A", 1_000));

        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);

        try {
            var f1 = pool.submit(() -> {
                start.await();

                try {
                    service.credit("A", 100, "TX-1");
                    return null;
                } catch (InvalidTransactionException e) {
                    return e;
                }
            });

            var f2 = pool.submit(() -> {
                start.await();

                try {
                    service.credit("A", 200, "TX-1");
                    return null;
                } catch (InvalidTransactionException e) {
                    return e;
                }
            });

            start.countDown();

            var e1 = f1.get(10, TimeUnit.SECONDS);
            var e2 = f2.get(10, TimeUnit.SECONDS);

            assertTrue(
                    (e1 == null && e2 != null)
                            || (e1 != null && e2 == null)
            );

            assertTrue(
                    service.getBalance("A") == 1_100
                            || service.getBalance("A") == 1_200
            );

        } finally {
            pool.shutdown();

            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void transactionIdCannotBeReusedForDifferentTransfer() {
        repo.save(new Account("A", 1_000));
        repo.save(new Account("B", 500));
        repo.save(new Account("C", 500));

        service.transfer(
                "A",
                "B",
                100,
                "TX-1"
        );

        assertThrows(
                InvalidTransactionException.class,
                () -> service.transfer(
                        "A",
                        "C",
                        100,
                        "TX-1"
                )
        );

        assertEquals(900, service.getBalance("A"));
        assertEquals(600, service.getBalance("B"));
        assertEquals(500, service.getBalance("C"));
    }

    @Test
    void transactionIdCannotBeReusedAcrossOperations() {
        repo.save(new Account("A", 1_000));

        service.credit(
                "A",
                100,
                "TX-1"
        );

        assertThrows(
                InvalidTransactionException.class,
                () -> service.debit(
                        "A",
                        100,
                        "TX-1"
                )
        );

        assertEquals(
                1_100,
                service.getBalance("A")
        );
    }

    @Test
    void creditOverflowDoesNotModifyBalance() {
        repo.save(
                new Account(
                        "A",
                        Long.MAX_VALUE
                )
        );

        assertThrows(
                InvalidTransactionException.class,
                () -> service.credit(
                        "A",
                        1,
                        "OVERFLOW"
                )
        );

        assertEquals(
                Long.MAX_VALUE,
                service.getBalance("A")
        );
    }

    @Test
    void failedTransferDoesNotMutateEitherAccount() {
        repo.save(new Account("A", 100));
        repo.save(new Account("B", 500));

        assertThrows(
                InsufficientFundsException.class,
                () -> service.transfer(
                        "A",
                        "B",
                        200,
                        "T1"
                )
        );

        assertEquals(
                100,
                service.getBalance("A")
        );

        assertEquals(
                500,
                service.getBalance("B")
        );
    }

    @Test
    void concurrentTransfersPreserveTotalBalance() throws Exception {
        repo.save(new Account("A", 1_000_000));
        repo.save(new Account("B", 1_000_000));

        int n = 2_000;

        var pool = Executors.newFixedThreadPool(32);
        var start = new CountDownLatch(1);
        var futures = new ArrayList<Future<?>>();

        try {
            for (int i = 0; i < n; i++) {
                int tx = i;

                futures.add(pool.submit(() -> {
                    start.await();

                    if ((tx & 1) == 0) {
                        service.transfer(
                                "A",
                                "B",
                                100,
                                "AB-" + tx
                        );
                    } else {
                        service.transfer(
                                "B",
                                "A",
                                100,
                                "BA-" + tx
                        );
                    }

                    return null;
                }));
            }

            start.countDown();

            for (var future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            long a = service.getBalance("A");
            long b = service.getBalance("B");

            assertEquals(
                    2_000_000,
                    a + b
            );

            assertEquals(
                    1_000_000,
                    a
            );

            assertEquals(
                    1_000_000,
                    b
            );

        } finally {
            pool.shutdown();

            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }

    @Test
    void concurrentDuplicateDebitAppliesOnce() throws Exception {
        repo.save(new Account("A", 10_000));

        int n = 100;

        var pool = Executors.newFixedThreadPool(32);
        var start = new CountDownLatch(1);
        var futures = new ArrayList<Future<?>>();

        try {
            for (int i = 0; i < n; i++) {
                futures.add(pool.submit(() -> {
                    start.await();

                    service.debit(
                            "A",
                            500,
                            "SAME-DEBIT"
                    );

                    return null;
                }));
            }

            start.countDown();

            for (var future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }

            assertEquals(
                    9_500,
                    service.getBalance("A")
            );

        } finally {
            pool.shutdown();

            if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                pool.shutdownNow();
            }
        }
    }

}

