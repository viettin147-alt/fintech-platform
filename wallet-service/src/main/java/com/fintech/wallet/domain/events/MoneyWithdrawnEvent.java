package com.fintech.wallet.domain.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MoneyWithdrawnEvent (
    String walletId,
    BigDecimal amount,
    long version,
    LocalDateTime occurredOn
) implements DomainEvent {
    public MoneyWithdrawnEvent(String walletId, BigDecimal amount, long version) {
        this(walletId, amount, version, LocalDateTime.now());
    }

    @Override
    public String aggregateId() {
        return walletId;
    }
}
