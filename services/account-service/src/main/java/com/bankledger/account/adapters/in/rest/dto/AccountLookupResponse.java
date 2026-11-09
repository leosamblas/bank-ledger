package com.bankledger.account.adapters.in.rest.dto;

import java.util.UUID;

public record AccountLookupResponse(
        UUID accountId,
        String agency,
        String number,
        String holderNameMasked
) {
}

