package com.bankledger.auth.application.port.out;

import com.bankledger.auth.domain.event.AccountReadyEvent;
import reactor.core.publisher.Mono;

public interface AccountReadyEventPublisherPort {
    Mono<Void> publish(AccountReadyEvent event);
}
