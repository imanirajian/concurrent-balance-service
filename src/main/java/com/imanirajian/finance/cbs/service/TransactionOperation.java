package com.imanirajian.finance.cbs.service;

import com.imanirajian.finance.cbs.domain.transaction.TransactionOutcome;

/**
 * @author Iman Irajian
 * Date: 8/18/2026 12:25 AM
 */

@FunctionalInterface
public interface TransactionOperation {

    TransactionOutcome execute();

}