package com.imanirajian.finance.cbs.domain.exception;

/**
 * @author Iman Irajian
 * Date: 8/15/2026 1:29 AM
 */

public final class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String id) {
        super("Account not found: " + id);
    }
}
