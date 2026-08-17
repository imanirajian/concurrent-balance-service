package com.imanirajian.finance.cbs.domain.exception;

/**
 * @author Iman Irajian
 * Date: 8/15/2026 1:30 AM
 */

public final class InvalidAmountException extends RuntimeException {
    public InvalidAmountException(long amount) {
        super("Amount must be greater than zero: " + amount);
    }
}