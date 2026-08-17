package com.imanirajian.finance.cbs.repository;

import com.imanirajian.finance.cbs.domain.Account;

/**
 * @author Iman Irajian
 * Date: 8/15/2026 1:34 AM
 */

public interface AccountRepository {

    Account findRequired(String accountId);

    void save(Account account);

    Account create(String accountId, long initialBalance);

}

