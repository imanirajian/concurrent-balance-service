package com.imanirajian.finance.cbs.service;

/**
 * @author Iman Irajian
 * Date: 8/15/2026 1:36 AM
 */

public interface BalanceService {

    void credit(String accountId, long amount, String transactionId);

    void debit(String accountId, long amount, String transactionId);

    void transfer(String sourceAccountId, String destinationAccountId, long amount, String transactionId);

    long getBalance(String accountId);

    void createAccount(String accountId, long initialBalance);

}
