package com.fintech.wallet.interfaces.rest;

import com.fintech.wallet.domain.repository.WalletEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class WalletConcurrencyTest {
    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    WalletEventRepository eventRepository;

    private final String testWalletId = "wallet-race-condition-test";

    @BeforeEach
    public void setUp() {
        eventRepository.clearAllEvents();
        // TẠO ĐIỀU KIỆN BAN ĐẦU: Nạp sẵn 50,000đ vào ví test trước khi race condition
        String depositUrl = "/api/wallets/" + testWalletId + "/deposit?amount=50000";
        restTemplate.postForEntity(depositUrl, null, String.class);
        System.out.println("--- ĐÃ KHỞI TẠO VÍ VỚI SỐ DƯ: 50,000đ ---");
    }
    @Test
    public void testRaceCondition_TwoWithdrawsParallel() throws InterruptedException {
        String withdrawUrl = "/api/wallets/" + testWalletId + "/withdraw?amount=50000";
        int numberOfThreads = 2;
        ExecutorService service = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(1);

        // Luồng thứ 1: Rút 50k
        service.submit(() -> {
            try {
                latch.await();
                restTemplate.postForEntity(withdrawUrl, null, String.class);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
// Luồng thứ 2: Cũng rút 50k song song
        service.submit(() -> {
            try {
                latch.await(); // Đứng chờ súng lệnh
                restTemplate.postForEntity(withdrawUrl, null, String.class);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // NỔ SÚNG! Cho phép cả 2 luồng cùng bắn API một lúc
        latch.countDown();

        // Chờ 2 giây cho cả 2 request xử lý xong xuôi
        Thread.sleep(2000);

        // KẾT QUẢ MONG MUỐN (BẢO VỆ FINTECH):
        // Vì ban đầu chỉ có 50k, nên dù có 2 request song song, chỉ được phép 1 request THÀNH CÔNG (200)
        // và 1 request phải THẤT BẠI (400). Số dư cuối cùng bắt buộc phải bằng 0, không được âm!

        // Hãy kiểm tra số dư thực tế bằng cách tải lại lịch sử từ DB lên thông qua logic Domain của bạn
        var history = eventRepository.loadEvents(testWalletId);
        var account = com.fintech.wallet.domain.models.WalletAccount.replayFromHistory(
                com.fintech.wallet.domain.models.WalletId.generate(), "USER-123", history);

        System.out.println("====== KẾT QUẢ CUỐI CÙNG ======");
        System.out.println("Số dư thực tế trong DB sau cuộc đua: " + account.getBalance());

        // Nếu hệ thống của bạn CHƯA BẢO MẬT (bị lỗi Race Condition):
        // Cả 2 request cùng thành công -> số dư sẽ bị âm -50,000đ -> Dòng assert dưới đây sẽ báo LỖI (Fail)!
        assertEquals(BigDecimal.ZERO.compareTo(account.getBalance()), 0,
                "CẢNH BÁO: Hệ thống bị lỗi Race Condition! Số dư bị âm tiền!");

    }
}
