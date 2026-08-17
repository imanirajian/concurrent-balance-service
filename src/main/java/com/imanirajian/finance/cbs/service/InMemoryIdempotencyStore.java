package com.imanirajian.finance.cbs.service;

import com.imanirajian.finance.cbs.domain.exception.InvalidTransactionException;
import com.imanirajian.finance.cbs.domain.transaction.TransactionFingerprint;
import com.imanirajian.finance.cbs.domain.transaction.TransactionOutcome;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Iman Irajian
 * Date: 8/15/2026 1:36 AM
 */

@Component
public final class InMemoryIdempotencyStore implements IdempotencyStore {

    private final ConcurrentMap<String, Entry> transactions = new ConcurrentHashMap<>();

    @Override
    public TransactionOutcome executeOnce(String transactionId, TransactionFingerprint fingerprint,
                                          TransactionOperation operation) {
        Entry candidate = new Entry(fingerprint);

        Entry existing = transactions.putIfAbsent(transactionId, candidate);

        if (existing != null) {
            verifyFingerprint(transactionId, fingerprint, existing.fingerprint());
            return existing.result().join();
        }

        try {
            TransactionOutcome outcome = operation.execute();
            candidate.result().complete(outcome);
            return outcome;
        } catch (RuntimeException ex) {
            candidate.result().completeExceptionally(ex);
            transactions.remove(transactionId, candidate);
            throw ex;
        }
    }

    private void verifyFingerprint(String transactionId, TransactionFingerprint actual,
                                   TransactionFingerprint existing) {
        if (!existing.equals(actual)) {
            throw new InvalidTransactionException("Transaction ID '" + transactionId
                    + "' was already used for a different operation"
            );
        }
    }

    private record Entry(TransactionFingerprint fingerprint, CompletableFuture<TransactionOutcome> result) {
        private Entry(TransactionFingerprint fingerprint) {
            this(fingerprint, new CompletableFuture<>());
        }
    }
}