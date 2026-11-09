package com.bankledger.account.application.port.in;

import java.util.UUID;

/**
 * Inbound port for retrieving authenticated customer account details.
 */
public interface GetAccountMeUseCase {

    AccountMeResult getAccountMe(UUID accountId);

    AccountMeResult getAccountByCustomerId(UUID customerId);
}
