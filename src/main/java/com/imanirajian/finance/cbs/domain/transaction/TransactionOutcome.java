package com.imanirajian.finance.cbs.domain.transaction;

/**
 * @author Iman Irajian
 * Date: 8/15/2026 1:30 AM
 */

public record TransactionOutcome(TransactionFingerprint fingerprint, boolean success, RuntimeException failure) {
    public static TransactionOutcome success(TransactionFingerprint fp) {
        return new TransactionOutcome(fp, true, null);
    }

    public static TransactionOutcome failure(TransactionFingerprint fp, RuntimeException ex) {
        return new TransactionOutcome(fp, false, ex);
    }

    public void rethrowIfFailed() {
        if (!success) throw failure;
    }
}
