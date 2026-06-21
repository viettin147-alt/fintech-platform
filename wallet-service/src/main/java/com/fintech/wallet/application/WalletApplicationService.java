package com.fintech.wallet.application;

import com.fintech.wallet.domain.events.DomainEvent;
import com.fintech.wallet.domain.exceptions.OptimisticLockException;
import com.fintech.wallet.domain.models.WalletAccount;
import com.fintech.wallet.domain.models.WalletId;
import com.fintech.wallet.domain.repository.WalletEventRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class WalletApplicationService {

    private static final int MAX_RETRIES = 3;
    private final WalletEventRepository eventRepository;

    public WalletApplicationService(WalletEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public void depositMoney(String walletIdValue, BigDecimal amount) {
        WalletId walletId = new WalletId(walletIdValue);

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                List<DomainEvent> history = eventRepository.loadEvents(walletIdValue);
                WalletAccount account = WalletAccount.replayFromHistory(walletId, "USER-123", history);
                System.out.println("(Deposit) Lan thu " + attempt + " - So du truoc khi nap: " + account.getBalance());

                account.deposit(amount);
                eventRepository.saveEvents(account.getChanges());

                System.out.println("(Deposit) Thanh cong! So du sau khi nap: " + account.getBalance());
                account.clearChanges();
                return;

            } catch (OptimisticLockException e) {
                System.out.println("(Deposit) Xung dot version lan " + attempt + ", dang thu lai...");
                if (attempt == MAX_RETRIES) {
                    throw new RuntimeException("He thong qua tai! Da thu " + MAX_RETRIES + " lan nhung van bi xung dot.", e);
                }
            }
        }
    }

    public void withdrawMoney(String walletIdValue, BigDecimal amount) {
        WalletId walletId = new WalletId(walletIdValue);

            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    List<DomainEvent> history = eventRepository.loadEvents(walletIdValue);
                    WalletAccount account = WalletAccount.replayFromHistory(walletId, "USER-123", history);
                    System.out.println("(Withdraw) Lan thu " + attempt + " - So du truoc khi rut: " + account.getBalance());

                    account.withdraw(amount);
                    eventRepository.saveEvents(account.getChanges());

                    System.out.println("(Withdraw) Thanh cong! So du sau khi rut: " + account.getBalance());
                    account.clearChanges();
                    return;

                } catch (OptimisticLockException e) {
                    System.out.println("(Withdraw) Xung dot version lan " + attempt + ", dang thu lai...");
                    if (attempt == MAX_RETRIES) {
                        throw new RuntimeException("He thong qua tai! Da thu " + MAX_RETRIES + " lan nhung van bi xung dot.", e);
                    }
                }
                // InsufficientBalanceException KHONG bi catch -> bay thang len Controller
            }
    }

    /**
     * Xem so du hien tai cua vi bang cach replay toan bo lich su su kien.
     */
    public BigDecimal getBalance(String walletIdValue) {
        WalletId walletId = new WalletId(walletIdValue);
        List<DomainEvent> history = eventRepository.loadEvents(walletIdValue);

        if (history.isEmpty()) {
            return BigDecimal.ZERO;
        }

        WalletAccount account = WalletAccount.replayFromHistory(walletId, "USER-123", history);
        return account.getBalance();
    }

}
