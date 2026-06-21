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

    // Danh sách lưu tạm các event mới sinh ra trước khi lưu xuống Event Store
    private final List<DomainEvent> changes = new ArrayList<>();

    // Constructor dùng khi khởi tạo ví mới tinh
    public WalletAccount(WalletId id, String userId) {
        this.id = id;
        this.userId = userId;
        this.balance = BigDecimal.ZERO;
    }

    // NGHIỆP VỤ: Nạp tiền
    public void deposit(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền nạp phải lớn hơn 0");
        }

        long nextVersion = this.version + 1;
        // Tạo ra sự kiện (Nhưng chưa làm thay đổi số dư ngay)
        MoneyDepositedEvent event = new MoneyDepositedEvent(this.id.value(), amount, nextVersion);

        // Ghi nhận sự kiện này vào hàng đợi thay đổi
        this.changes.add(event);

        // Áp dụng sự kiện để thực sự thay đổi trạng thái số dư
        this.apply(event);
    }

    public void withdraw(BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Số tiền rút phải lớn hơn 0");
        }

        // Kiểm tra số dư hiện tại
        // Chỉ cấm khi số dư < số tiền muốn rút. Nếu bằng nhau (rút hết) thì vẫn hợp lệ.
        if (this.balance.compareTo(amount) < 0) {
            throw new IllegalArgumentException("Số dư không đủ để rút");
        }

        long nextVersion = this.version + 1;
        MoneyWithdrawnEvent event = new MoneyWithdrawnEvent(this.id.value(), amount, nextVersion);
        this.changes.add(event);
        this.apply(event);
    }

    // Hàm nội bộ giải mã Event và cập nhật trạng thái
    private void apply(DomainEvent event) {
        if (event instanceof MoneyDepositedEvent e) {
            this.balance = this.balance.add(e.amount());
        }
        else if  (event instanceof MoneyWithdrawnEvent e) {
            this.balance = this.balance.subtract(e.amount());
        }
        this.version = event.version();
    }

    // TÁI TẠO TRẠNG THÁI (Dùng cho luồng Replay từ DB sau này)
    public static WalletAccount replayFromHistory(WalletId id, String userId, List<DomainEvent> history) {
        WalletAccount account = new WalletAccount(id, userId);
        for (DomainEvent event : history) {
            account.apply(event);
        }
        return account;
    }

    // Getters
    public WalletId getId() { return id; }
    public String getUserId() { return userId; }
    public BigDecimal getBalance() { return balance; }
    public List<DomainEvent> getChanges() { return changes; }
    public void clearChanges() { this.changes.clear(); }
}