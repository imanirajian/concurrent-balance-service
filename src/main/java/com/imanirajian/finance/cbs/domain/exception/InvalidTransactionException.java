package com.imanirajian.finance.cbs.domain.exception;

/**
 * @author Iman Irajian
 * Date: 8/15/2026 1:30 AM
 */

public final class InvalidTransactionException extends RuntimeException {
    public InvalidTransactionException(String message) {
        super(message);
    }
}
