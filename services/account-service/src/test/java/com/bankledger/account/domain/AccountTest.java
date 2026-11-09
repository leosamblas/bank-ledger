package com.bankledger.account.domain;

import com.bankledger.account.domain.exception.AccountAlreadyClosedException;
import com.bankledger.account.domain.model.Account;
import com.bankledger.account.domain.model.AccountStatus;
import com.bankledger.account.domain.vo.AccountNumber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    private static final AccountNumber ACCOUNT_NUMBER = AccountNumber.of("100000");

    @Test
    @DisplayName("Should create account with default branch '0001' and ACTIVE status")
    void shouldCreateAccountWithDefaultBranchAndActiveStatus() {
        UUID customerId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-09T10:00:00Z");

        Account account = Account.create(customerId, ACCOUNT_NUMBER, now);

        assertThat(account.getId()).isNotNull();
        assertThat(account.getCustomerId()).isEqualTo(customerId);
        assertThat(account.getBranch()).isEqualTo("0001");
        assertThat(account.getAccountNumber()).isEqualTo(ACCOUNT_NUMBER);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.getCreatedAt()).isEqualTo(now);
        assertThat(account.getUpdatedAt()).isEqualTo(now);
        assertThat(account.getClosedAt()).isNull();
    }

    @Test
    @DisplayName("Should close active account and record closedAt timestamp")
    void shouldCloseActiveAccount() {
        UUID customerId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-11-09T10:00:00Z");
        Instant closedAt = Instant.parse("2026-11-09T12:00:00Z");

        Account account = Account.create(customerId, ACCOUNT_NUMBER, createdAt);
        account.close(closedAt);

        assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
        assertThat(account.getClosedAt()).isEqualTo(closedAt);
        assertThat(account.getUpdatedAt()).isEqualTo(closedAt);
    }

    @Test
    @DisplayName("Should throw AccountAlreadyClosedException when closing already closed account")
    void shouldThrowWhenClosingAlreadyClosedAccount() {
        UUID customerId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-11-09T10:00:00Z");
        Instant closedAt = Instant.parse("2026-11-09T12:00:00Z");

        Account account = Account.create(customerId, ACCOUNT_NUMBER, createdAt);
        account.close(closedAt);

        assertThatThrownBy(() -> account.close(closedAt.plusSeconds(60)))
                .isInstanceOf(AccountAlreadyClosedException.class);
    }

    @Test
    @DisplayName("Should reconstitute account with all attributes")
    void shouldReconstituteAccount() {
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-11-01T10:00:00Z");
        Instant updatedAt = Instant.parse("2026-11-05T10:00:00Z");

        Account account = new Account(
                id,
                customerId,
                "0002",
                ACCOUNT_NUMBER,
                AccountStatus.SUSPENDED,
                createdAt,
                updatedAt,
                null
        );

        assertThat(account.getId()).isEqualTo(id);
        assertThat(account.getCustomerId()).isEqualTo(customerId);
        assertThat(account.getBranch()).isEqualTo("0002");
        assertThat(account.getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(account.getCreatedAt()).isEqualTo(createdAt);
        assertThat(account.getUpdatedAt()).isEqualTo(updatedAt);
        assertThat(account.getClosedAt()).isNull();
    }
}
