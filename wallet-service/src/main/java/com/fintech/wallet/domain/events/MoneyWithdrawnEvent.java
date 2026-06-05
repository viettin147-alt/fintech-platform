package com.fintech.wallet.domain.events;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MoneyWithdrawnEvent (
    String walletId,
    BigDecimal amount,
    LocalDateTime occurredOn
) implements DomainEvent {
    public MoneyWithdrawnEvent(String walletId, BigDecimal amount) {
        this(walletId, amount, LocalDateTime.now());
    }

    @Override
    public String aggregateId() {
        return walletId;
    }
}
