package com.imanirajian.finance.cbs.service;

import com.imanirajian.finance.cbs.domain.transaction.TransactionFingerprint;
import com.imanirajian.finance.cbs.domain.transaction.TransactionOutcome;

/**
 * @author Iman Irajian
 * Date: 8/15/2026 1:36 AM
 */

public interface IdempotencyStore {

    TransactionOutcome executeOnce(
            String transactionId,
            TransactionFingerprint fingerprint,
            TransactionOperation operation
    );

}