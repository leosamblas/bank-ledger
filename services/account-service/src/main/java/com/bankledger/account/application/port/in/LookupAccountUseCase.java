package com.bankledger.account.application.port.in;

/**
 * Inbound port for looking up account details with masked customer name.
 */
public interface LookupAccountUseCase {

    AccountLookupResult lookup(LookupAccountQuery query);

    AccountLookupResult lookup(String branch, String accountNumber);
}
