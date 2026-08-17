package com.imanirajian.finance.cbs.domain.transaction;

import com.imanirajian.finance.cbs.domain.OperationType;

/**
 * @author Iman Irajian
 * Date: 8/15/2026 1:30 AM
 */

public record TransactionFingerprint(OperationType operationType, String sourceAccountId, String destinationAccountId,
                                     long amount) {
    public static TransactionFingerprint from(Transaction tx) {
        return new TransactionFingerprint(tx.operationType(), tx.sourceAccountId(), tx.destinationAccountId(), tx.amount());
    }
}
