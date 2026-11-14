package com.bankledger.account.domain;

import com.bankledger.account.domain.event.AccountOpenedEvent;
import com.bankledger.account.domain.model.Account;
import com.bankledger.account.domain.model.Customer;
import com.bankledger.commons.constant.KafkaTopics;
import com.bankledger.account.domain.vo.AccountNumber;
import com.bankledger.account.domain.vo.Cpf;
import com.bankledger.account.domain.vo.Email;
import com.bankledger.account.domain.vo.Phone;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountOpenedEventTest {

    @Test
    @DisplayName("Should create AccountOpenedEvent from customer and account")
    void shouldCreateAccountOpenedEventFromModels() {
        Instant now = Instant.parse("2026-11-09T10:00:00Z");

        Customer customer = Customer.create(
                UUID.randomUUID(),
                "Alice Wonder",
                new Cpf("52998224725"),
                new Email("alice@example.com"),
                new Phone("+5511987654321"),
                LocalDate.of(1990, 1, 1),
                "hash",
                now
        );

        Account account = Account.create(customer.getId(), AccountNumber.of("100000"), now);

        AccountOpenedEvent event = AccountOpenedEvent.of(account, customer);

        assertThat(event.accountId()).isEqualTo(account.getId());
        assertThat(event.customerId()).isEqualTo(customer.getId());
        assertThat(event.customerCpf()).isEqualTo("52998224725");
        assertThat(event.customerName()).isEqualTo("Alice Wonder");
        assertThat(event.branch()).isEqualTo("0001");
        assertThat(event.accountNumber()).isEqualTo("100000");
        assertThat(event.checkDigit()).isEqualTo("4");
        assertThat(event.openedAt()).isEqualTo(now);
        assertThat(AccountOpenedEvent.TOPIC).isEqualTo(KafkaTopics.ACCOUNT_EVENTS_V1);
        assertThat(AccountOpenedEvent.EVENT_TYPE).isEqualTo("AccountOpened");
    }

    @Test
    @DisplayName("Should reject null fields in AccountOpenedEvent")
    void shouldRejectNullFields() {
        assertThatThrownBy(() -> new AccountOpenedEvent(
                null,
                UUID.randomUUID(),
                "52998224725",
                "Alice",
                "0001",
                "100000",
                "4",
                Instant.now()
        )).isInstanceOf(NullPointerException.class);
    }
}
