package com.fintech.wallet.domain.models;

import com.fintech.wallet.domain.events.DomainEvent;
import com.fintech.wallet.domain.events.MoneyDepositedEvent;
import com.fintech.wallet.domain.events.MoneyWithdrawnEvent;
import com.fintech.wallet.domain.exceptions.InsufficientBalanceException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class WalletAccount {
    private final WalletId id;
    private final String userId;
    private BigDecimal balance;
    private long version = 0;

    private final List<DomainEvent> changes = new ArrayList<>();

    public WalletAccount(WalletId id, String userId) {
        this.id = id;
        this.userId = userId;
        this.balance = BigDecimal.ZERO;
    }

    public void deposit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("So tien nap phai lon hon 0");
        }
        long nextVersion = this.version + 1;
        MoneyDepositedEvent event = new MoneyDepositedEvent(this.id.value(), amount, nextVersion);
        this.changes.add(event);
        this.apply(event);
    }

    public void withdraw(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("So tien rut phai lon hon 0");
        }
        if (this.balance.compareTo(amount) < 0) {
            throw new InsufficientBalanceException(this.balance, amount);
        }
        long nextVersion = this.version + 1;
        MoneyWithdrawnEvent event = new MoneyWithdrawnEvent(this.id.value(), amount, nextVersion);
        this.changes.add(event);
        this.apply(event);
    }

    private void apply(DomainEvent event) {
        if (event instanceof MoneyDepositedEvent e) {
            this.balance = this.balance.add(e.amount());
        } else if (event instanceof MoneyWithdrawnEvent e) {
            this.balance = this.balance.subtract(e.amount());
        }
        this.version = event.version();
    }

    public static WalletAccount replayFromHistory(WalletId id, String userId, List<DomainEvent> history) {
        WalletAccount account = new WalletAccount(id, userId);
        for (DomainEvent event : history) {
            account.apply(event);
        }
        return account;
    }

    public WalletId getId() { return id; }
    public String getUserId() { return userId; }
    public BigDecimal getBalance() { return balance; }
    public long getVersion() { return version; }
    public List<DomainEvent> getChanges() { return changes; }
    public void clearChanges() { this.changes.clear(); }
}
