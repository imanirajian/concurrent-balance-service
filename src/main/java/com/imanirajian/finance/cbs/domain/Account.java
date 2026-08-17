package com.imanirajian.finance.cbs.domain;

import com.imanirajian.finance.cbs.domain.exception.InsufficientFundsException;
import lombok.Getter;

import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Iman Irajian
 * Date: 8/15/2026 1:29 AM
 */

@Getter
public final class Account {

    private final String id;
    private final ReentrantLock lock = new ReentrantLock();
    private long balance;

    public Account(String id, long initialBalance) {
        if (id == null || id.isBlank())
            throw new IllegalArgumentException("accountId must not be blank");

        if (initialBalance < 0)
            throw new IllegalArgumentException("initialBalance must not be negative");

        this.id = id;
        this.balance = initialBalance;
    }

    public void credit(long amount) {
        balance = Math.addExact(balance, amount);
    }

    public void debit(long amount) {
        if (balance < amount)
            throw new InsufficientFundsException(id, amount, balance);

        balance -= amount;
    }

}
