package com.bankledger.query.adapters.in.rest.dto;

import java.io.Serializable;
import java.util.List;

public record StatementResponse(
        List<StatementItemResponse> items,
        String nextCursor
) implements Serializable {

    public StatementResponse(List<StatementItemResponse> items) {
        this(items, null);
    }
}
