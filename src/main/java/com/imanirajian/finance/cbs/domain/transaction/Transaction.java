package com.imanirajian.finance.cbs.domain.transaction;

import com.imanirajian.finance.cbs.domain.OperationType;

/**
 * @author Iman Irajian
 * Date: 8/15/2026 1:30 AM
 */

public record Transaction(String transactionId, OperationType operationType, String sourceAccountId,
                          String destinationAccountId, long amount) {
    public static Transaction credit(String id, String account, long amount) {
        return new Transaction(id, OperationType.CREDIT, account, null, amount);
    }

    public static Transaction debit(String id, String account, long amount) {
        return new Transaction(id, OperationType.DEBIT, account, null, amount);
    }

    public static Transaction transfer(String id, String source, String destination, long amount) {
        return new Transaction(id, OperationType.TRANSFER, source, destination, amount);
    }
}
