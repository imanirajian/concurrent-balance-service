package com.imanirajian.finance.cbs.domain.exception;

/**
 * @author Iman Irajian
 * Date: 8/15/2026 1:29 AM
 */

public final class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String id, long requested, long available) {
        super("Insufficient funds in account '%s': requested=%d, available=%d".formatted(id, requested, available));
    }
}

