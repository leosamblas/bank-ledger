package com.bankledger.auth.application.port.out;

import com.bankledger.auth.domain.event.AccountReadyEvent;

public interface AccountReadyEventPublisherPort {
    void publish(AccountReadyEvent event);
}
