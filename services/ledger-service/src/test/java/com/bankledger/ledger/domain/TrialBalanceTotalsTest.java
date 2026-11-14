package com.bankledger.ledger.domain;

import com.bankledger.ledger.domain.model.TrialBalanceTotals;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TrialBalanceTotals Domain Record Unit Tests")
class TrialBalanceTotalsTest {

    @Test
    @DisplayName("Should return 0 difference when debits equal credits")
    void shouldReturnZeroDifferenceWhenBalanced() {
        TrialBalanceTotals totals = new TrialBalanceTotals(50000L, 50000L);

        assertThat(totals.debits()).isEqualTo(50000L);
        assertThat(totals.credits()).isEqualTo(50000L);
        assertThat(totals.difference()).isZero();
    }

    @Test
    @DisplayName("Should return positive difference when debits exceed credits")
    void shouldReturnPositiveDifferenceWhenDebitsExceedCredits() {
        TrialBalanceTotals totals = new TrialBalanceTotals(70000L, 50000L);

        assertThat(totals.difference()).isEqualTo(20000L);
    }

    @Test
    @DisplayName("Should return negative difference when credits exceed debits")
    void shouldReturnNegativeDifferenceWhenCreditsExceedDebits() {
        TrialBalanceTotals totals = new TrialBalanceTotals(40000L, 50000L);

        assertThat(totals.difference()).isEqualTo(-10000L);
    }

    @Test
    @DisplayName("Should handle zero totals properly")
    void shouldHandleZeroTotals() {
        TrialBalanceTotals totals = new TrialBalanceTotals(0L, 0L);

        assertThat(totals.debits()).isZero();
        assertThat(totals.credits()).isZero();
        assertThat(totals.difference()).isZero();
    }
}
