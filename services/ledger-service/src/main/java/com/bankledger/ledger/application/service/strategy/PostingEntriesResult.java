package com.bankledger.ledger.application.service.strategy;

import com.bankledger.ledger.domain.model.Entry;

import java.util.Collections;
import java.util.List;

/**
 * Result of generating double-entry legs for a journal posting.
 * Holds either the generated entries or rejection metadata.
 */
public record PostingEntriesResult(
        List<Entry> entries,
        String rejectionCode,
        String rejectionReason
) {

    public PostingEntriesResult {
        entries = (entries != null) ? List.copyOf(entries) : Collections.emptyList();
    }

    public static PostingEntriesResult success(List<Entry> entries) {
        return new PostingEntriesResult(entries, null, null);
    }

    public static PostingEntriesResult rejected(String rejectionCode, String rejectionReason) {
        return new PostingEntriesResult(Collections.emptyList(), rejectionCode, rejectionReason);
    }

    public boolean isRejected() {
        return rejectionCode != null && !rejectionCode.isBlank();
    }
}
