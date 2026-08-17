package com.imanirajian.finance.cbs.service;

import com.imanirajian.finance.cbs.domain.Account;
import com.imanirajian.finance.cbs.domain.exception.InsufficientFundsException;
import com.imanirajian.finance.cbs.domain.exception.InvalidAmountException;
import com.imanirajian.finance.cbs.domain.exception.InvalidTransactionException;
import com.imanirajian.finance.cbs.domain.transaction.Transaction;
import com.imanirajian.finance.cbs.domain.transaction.TransactionFingerprint;
import com.imanirajian.finance.cbs.domain.transaction.TransactionOutcome;
import com.imanirajian.finance.cbs.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

/**
 * @author Iman Irajian
 * Date: 8/15/2026 1:36 AM
 */

@Service
@RequiredArgsConstructor
public final class ConcurrentBalanceService implements BalanceService {

    private final AccountRepository accounts;
    private final IdempotencyStore idempotency;

    @Override
    public void createAccount(String accountId, long initialBalance) {
        validateId(accountId, "accountId");

        if (initialBalance < 0) {
            throw new InvalidAmountException(initialBalance);
        }

        accounts.create(accountId, initialBalance);
    }

    @Override
    public void credit(String accountId, long amount, String transactionId) {
        validateSingle(accountId, amount, transactionId);

        var transaction = Transaction.credit(transactionId, accountId, amount);

        var fingerprint = TransactionFingerprint.from(transaction);

        idempotency.executeOnce(transactionId, fingerprint, () -> {
                    var account = accounts.findRequired(accountId);

                    return withLock(account, () -> {
                                try {
                                    account.credit(amount);

                                    return TransactionOutcome.success(
                                            fingerprint
                                    );
                                } catch (ArithmeticException ex) {
                                    return TransactionOutcome.failure(
                                            fingerprint,
                                            new InvalidTransactionException(
                                                    "Balance overflow"
                                            )
                                    );
                                }
                            }
                    );
                }
        ).rethrowIfFailed();
    }

    @Override
    public void debit(String accountId, long amount, String transactionId) {
        validateSingle(accountId, amount, transactionId);

        var transaction = Transaction.debit(transactionId, accountId, amount);

        var fingerprint = TransactionFingerprint.from(transaction);

        idempotency.executeOnce(transactionId, fingerprint, () -> {
                    var account = accounts.findRequired(accountId);

                    return withLock(account, () -> {
                                try {
                                    account.debit(amount);

                                    return TransactionOutcome.success(
                                            fingerprint
                                    );
                                } catch (InsufficientFundsException ex) {
                                    return TransactionOutcome.failure(
                                            fingerprint,
                                            ex
                                    );
                                } catch (ArithmeticException ex) {
                                    return TransactionOutcome.failure(
                                            fingerprint,
                                            new InvalidTransactionException(
                                                    "Balance overflow"
                                            )
                                    );
                                }
                            }
                    );
                }
        ).rethrowIfFailed();
    }

    @Override
    public void transfer(String sourceId, String destinationId, long amount, String transactionId) {
        validateTransfer(sourceId, destinationId, amount, transactionId);

        if (sourceId.equals(destinationId)) {
            throw new InvalidTransactionException("Source and destination accounts must be different");
        }

        var transaction = Transaction.transfer(transactionId, sourceId, destinationId, amount);

        var fingerprint = TransactionFingerprint.from(transaction);

        idempotency.executeOnce(transactionId, fingerprint, () -> {
                    var source = accounts.findRequired(sourceId);

                    var destination = accounts.findRequired(destinationId);

                    return withOrderedLocks(source, destination, () -> executeTransfer(source, destination, amount, fingerprint));
                }
        ).rethrowIfFailed();
    }

    @Override
    public long getBalance(String accountId) {
        validateId(accountId, "accountId");

        var account = accounts.findRequired(accountId);

        return withLock(account, account::getBalance);
    }

    private TransactionOutcome executeTransfer(Account source, Account destination, long amount, TransactionFingerprint fingerprint) {
        /*
         * Validate every condition BEFORE mutating either account.
         */
        if (source.getBalance() < amount) {
            return TransactionOutcome.failure(fingerprint,
                    new InsufficientFundsException(source.getId(), amount, source.getBalance())
            );
        }

        try {
            Math.addExact(destination.getBalance(), amount);
        } catch (ArithmeticException ex) {
            return TransactionOutcome.failure(
                    fingerprint,
                    new InvalidTransactionException(
                            "Balance overflow"
                    )
            );
        }

        /*
         * Both locks are held here.
         *
         * Therefore another operation cannot observe a partially
         * completed transfer through the public service API.
         */
        source.debit(amount);
        destination.credit(amount);

        return TransactionOutcome.success(fingerprint);
    }

    private <T> T withLock(Account account, Supplier<T> operation) {
        Lock lock = account.getLock();

        lock.lock();
        try {
            return operation.get();
        } finally {
            lock.unlock();
        }
    }

    private <T> T withOrderedLocks(Account firstAccount, Account secondAccount, Supplier<T> operation) {
        Account first = firstAccount.getId().compareTo(secondAccount.getId()) <= 0 ? firstAccount : secondAccount;

        Account second = first == firstAccount ? secondAccount : firstAccount;

        Lock firstLock = first.getLock();
        Lock secondLock = second.getLock();

        firstLock.lock();
        try {
            secondLock.lock();
            try {
                return operation.get();
            } finally {
                secondLock.unlock();
            }
        } finally {
            firstLock.unlock();
        }
    }

    private void validateSingle(String accountId, long amount, String transactionId) {
        validateId(accountId, "accountId");
        validateAmount(amount);
        validateId(transactionId, "transactionId");
    }

    private void validateTransfer(String sourceId, String destinationId, long amount, String transactionId) {
        validateId(sourceId, "sourceAccountId");
        validateId(destinationId, "destinationAccountId");
        validateAmount(amount);
        validateId(transactionId, "transactionId");
    }

    private void validateId(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new InvalidTransactionException(name + " must not be blank");
        }
    }

    private void validateAmount(long amount) {
        if (amount <= 0) {
            throw new InvalidAmountException(amount);
        }
    }

}