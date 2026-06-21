package com.fintech.wallet.domain.events;

import java.time.LocalDateTime;

public interface DomainEvent {
    String aggregateId();
    long version();
    LocalDateTime occurredOn();
}