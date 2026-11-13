package com.bankledger.ledger.application.port.in;

import com.bankledger.ledger.domain.event.AccountOpenedPayload;

/**
 * Inbound port for provisioning a ledger account upon receiving an AccountOpened event.
 */
public interface ProvisionAccountUseCase {

    /**
     * Provisions a new ledger account for the given account opening payload idempotently.
     *
     * @param payload incoming account opened event payload
     */
    void provisionAccount(AccountOpenedPayload payload);
}
