package com.fintech.wallet.application;

import com.fintech.wallet.domain.events.DomainEvent;
import com.fintech.wallet.domain.models.WalletAccount;
import com.fintech.wallet.domain.models.WalletId;
import com.fintech.wallet.domain.repository.WalletEventRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class WalletApplicationService {

//    // Giả lập một Event Store bằng HashMap trên RAM để lưu trữ chuỗi sự kiện của từng Ví
//    private final Map<String, List<DomainEvent>> eventStoreMock = new HashMap<>();
    private final WalletEventRepository eventRepository;

    public WalletApplicationService(WalletEventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    public void depositMoney(String walletIdValue, BigDecimal amount) {
        WalletId walletId = new WalletId(walletIdValue);

        // 1. Lấy lịch sử sự kiện từ Event Store (Nếu chưa có thì tạo mảng rỗng)
//        List<DomainEvent> history = eventStoreMock.getOrDefault(walletIdValue, new ArrayList<>());
        List<DomainEvent> history = eventRepository.loadEvents(walletIdValue);

        // 2. REPLAY: Tái tạo lại trạng thái ví từ lịch sử sự kiện
        WalletAccount account = WalletAccount.replayFromHistory(walletId, "USER-123", history);
        System.out.println("[Application] Số dư trước khi nạp: " + account.getBalance());

        // 3. Thực thi nghiệp vụ nạp tiền bên trong Domain
        account.deposit(amount);

        // 4. Lấy các sự kiện mới sinh ra và append (ghi thêm) vào Event Store
        List<DomainEvent> newEvents = account.getChanges();
        history.addAll(newEvents);
//        eventStoreMock.put(walletIdValue, history);
        eventRepository.saveEvents(account.getChanges());


        System.out.println("[Application] Đã lưu thành công " + newEvents.size() + " sự kiện mới vào Event Store!");
        System.out.println("[Application] Số dư hiện tại sau khi nạp: " + account.getBalance());

        // Xóa danh sách sự kiện tạm trong thực thể để giải phóng bộ nhớ
        account.clearChanges();
    }

    public void withdrawMoney(String walletIdValue, BigDecimal amount) {
        WalletId walletId = new WalletId(walletIdValue);

        // 1. Lấy lịch sử sự kiện từ Event Store (Nếu chưa có thì tạo mảng rỗng)
        List<DomainEvent> history = eventRepository.loadEvents(walletIdValue);

        WalletAccount account = WalletAccount.replayFromHistory(walletId, "USER-123", history);
        System.out.println("[Application] Số dư trước khi rút: " + account.getBalance());

        account.withdraw(amount);

        List<DomainEvent> newEvents = account.getChanges();
        history.addAll(newEvents);
        eventRepository.saveEvents(account.getChanges());

        System.out.println("[Application] Đã lưu thành công " + newEvents.size() + " sự kiện mới vào Event Store!");
        System.out.println("[Application] Số dư hiện tại sau khi rút: " + account.getBalance());

        account.clearChanges();
    }

}
