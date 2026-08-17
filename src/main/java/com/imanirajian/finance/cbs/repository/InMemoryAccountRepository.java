package com.imanirajian.finance.cbs.repository;

import com.imanirajian.finance.cbs.domain.Account;
import com.imanirajian.finance.cbs.domain.exception.AccountNotFoundException;
import org.springframework.stereotype.Repository;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Iman Irajian
 * Date: 8/15/2026 1:34 AM
 */

@Repository
public final class InMemoryAccountRepository implements AccountRepository {

    private final ConcurrentHashMap<String, Account> accounts = new ConcurrentHashMap<>();

    public Account create(String id, long initialBalance) {
        Account account = new Account(id, initialBalance);
        if (accounts.putIfAbsent(id, account) != null) {
            throw new IllegalStateException("Account already exists: " + id);
        }
        return account;
    }

    public Account findRequired(String id) {
        var a = accounts.get(id);

        if (a == null)
            throw new AccountNotFoundException(id);

        return a;
    }

    public void save(Account account) {
        if (accounts.putIfAbsent(account.getId(), account) != null)
            throw new IllegalStateException("Account already exists: " + account.getId());
    }

}
