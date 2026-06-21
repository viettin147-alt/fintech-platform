package com.fintech.wallet.interfaces.rest;

import com.fintech.wallet.domain.events.DomainEvent;
import com.fintech.wallet.domain.models.WalletAccount;
import com.fintech.wallet.domain.models.WalletId;
import com.fintech.wallet.domain.repository.WalletEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

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
        String depositUrl = "/api/wallets/" + testWalletId + "/deposit?amount=50000";
        restTemplate.postForEntity(depositUrl, null, String.class);
        System.out.println("=== DA KHOI TAO VI VOI SO DU: 50,000 ===");
    }

    // === HELPER: Lay so du tu DB bang cach replay event ===
    private BigDecimal getBalanceFromDB() {
        List<DomainEvent> history = eventRepository.loadEvents(testWalletId);
        if (history.isEmpty()) return BigDecimal.ZERO;
        WalletAccount account = WalletAccount.replayFromHistory(
                WalletId.generate(), "USER-123", history);
        return account.getBalance();
    }

    // ======================================================================
    // TEST 1: Race Condition - 2 luong rut 50k dong thoi khi chi co 50k
    // ======================================================================
    @Test
    @DisplayName("Race Condition: 2 luong rut 50k dong thoi -> chi 1 thanh cong")
    public void testRaceCondition_TwoWithdrawsParallel() throws InterruptedException {
        String withdrawUrl = "/api/wallets/" + testWalletId + "/withdraw?amount=50000";
        int numberOfThreads = 2;
        ExecutorService service = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(1);

        // Dem so luong thanh cong that su (HTTP 200)
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        // Luong 1: Rut 50k
        service.submit(() -> {
            try {
                latch.await();
                ResponseEntity<String> response = restTemplate.postForEntity(withdrawUrl, null, String.class);
                if (response.getStatusCode() == HttpStatus.OK) {
                    successCount.incrementAndGet();
                } else {
                    failCount.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Luong 2: Cung rut 50k song song
        service.submit(() -> {
            try {
                latch.await();
                ResponseEntity<String> response = restTemplate.postForEntity(withdrawUrl, null, String.class);
                if (response.getStatusCode() == HttpStatus.OK) {
                    successCount.incrementAndGet();
                } else {
                    failCount.incrementAndGet();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // NO SUNG! Cho phep ca 2 luong cung ban API mot luc
        latch.countDown();

        // Cho 5 giay cho ca 2 request xu ly xong (bao gom retry)
        Thread.sleep(5000);

        // Kiem tra so du tu DB
        BigDecimal finalBalance = getBalanceFromDB();
        System.out.println("====== KET QUA TEST 1: RACE CONDITION ======");
        System.out.println("So luong request thanh cong (HTTP 200): " + successCount.get());
        System.out.println("So luong request that bai (HTTP 4xx/5xx): " + failCount.get());
        System.out.println("So du cuoi cung trong DB: " + finalBalance);

        // ASSERT: Chi duoc phep 1 request thanh cong
        assertEquals(1, successCount.get(), "Chi duoc 1 request thanh cong!");
        assertEquals(1, failCount.get(), "Phai co 1 request that bai!");

        // ASSERT: So du PHAI bang 0, KHONG DUOC AM!
        assertEquals(0, BigDecimal.ZERO.compareTo(finalBalance),
                "LOI RACE CONDITION! So du bi am tien!");

        service.shutdown();
    }

    // ======================================================================
    // TEST 2: Rut tien khong du -> InsufficientBalanceException -> HTTP 400
    // ======================================================================
    @Test
    @DisplayName("Rut tien khong du: rut 100k khi chi co 50k -> HTTP 400")
    public void testWithdrawInsufficientBalance_Returns400() {
        // Vi da co 50,000 tu setUp()
        String withdrawUrl = "/api/wallets/" + testWalletId + "/withdraw?amount=100000";

        ResponseEntity<Map> response = restTemplate.postForEntity(withdrawUrl, null, Map.class);

        System.out.println("====== KET QUA TEST 2: SO DU KHONG DU ======");
        System.out.println("HTTP Status: " + response.getStatusCode());
        System.out.println("Response body: " + response.getBody());

        // Assert: Phai tra ve 400 Bad Request
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode(),
                "Phai tra ve 400 khi so du khong du!");

        // Assert: So du khong thay doi (van la 50k)
        BigDecimal balanceAfter = getBalanceFromDB();
        assertEquals(0, new BigDecimal("50000").compareTo(balanceAfter),
                "So du khong duoc thay doi khi rut that bai!");
    }

    // ======================================================================
    // TEST 3: API GET Balance - xem so du
    // ======================================================================
    @Test
    @DisplayName("GET Balance: xem so du vi")
    public void testGetBalance_ReturnsCorrectBalance() {
        String balanceUrl = "/api/wallets/" + testWalletId + "/balance";

        ResponseEntity<Map> response = restTemplate.getForEntity(balanceUrl, Map.class);

        System.out.println("====== KET QUA TEST 3: GET BALANCE ======");
        System.out.println("HTTP Status: " + response.getStatusCode());
        System.out.println("Response body: " + response.getBody());

        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Kiem tra so du = 50,000 (tu setUp)
        Object balanceObj = response.getBody().get("balance");
        BigDecimal balance = new BigDecimal(balanceObj.toString());
        assertEquals(0, new BigDecimal("50000").compareTo(balance),
                "So du phai la 50,000!");
    }

    // ======================================================================
    // TEST 4: Nap + Rut binh thuong (khong race condition)
    // ======================================================================
    @Test
    @DisplayName("Nap + Rut binh thuong: nap 20k, rut 30k -> so du = 40k")
    public void testDepositAndWithdraw_NormalFlow() {
        // Vi da co 50,000 tu setUp()
        String depositUrl = "/api/wallets/" + testWalletId + "/deposit?amount=20000";
        String withdrawUrl = "/api/wallets/" + testWalletId + "/withdraw?amount=30000";

        // Nap them 20k -> tong = 70k
        ResponseEntity<String> depositResponse = restTemplate.postForEntity(depositUrl, null, String.class);
        assertEquals(HttpStatus.OK, depositResponse.getStatusCode(), "Nap tien that bai!");

        // Rut 30k -> con lai = 40k
        ResponseEntity<String> withdrawResponse = restTemplate.postForEntity(withdrawUrl, null, String.class);
        assertEquals(HttpStatus.OK, withdrawResponse.getStatusCode(), "Rut tien that bai!");

        // Kiem tra so du
        BigDecimal finalBalance = getBalanceFromDB();
        System.out.println("====== KET QUA TEST 4: NAP + RUT BINH THUONG ======");
        System.out.println("So du cuoi cung: " + finalBalance);

        assertEquals(0, new BigDecimal("40000").compareTo(finalBalance),
                "So du phai la 40,000 (50k + 20k - 30k)!");
    }

    // ======================================================================
    // TEST 5: Race Condition voi nhieu luong hon (5 luong rut 20k)
    // ======================================================================
    @Test
    @DisplayName("Race Condition nhieu luong: 5 luong rut 20k dong thoi khi chi co 50k")
    public void testRaceCondition_MultipleThreads() throws InterruptedException {
        // Vi da co 50,000 tu setUp()
        // 5 luong moi luong rut 20k -> chi duoc phep 2 luong thanh cong (tong 40k)
        // Luong thu 3 se that bai (con 10k, khong du 20k)
        String withdrawUrl = "/api/wallets/" + testWalletId + "/withdraw?amount=20000";
        int numberOfThreads = 5;
        ExecutorService service = Executors.newFixedThreadPool(numberOfThreads);
        CountDownLatch latch = new CountDownLatch(1);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < numberOfThreads; i++) {
            service.submit(() -> {
                try {
                    latch.await();
                    ResponseEntity<String> response = restTemplate.postForEntity(withdrawUrl, null, String.class);
                    if (response.getStatusCode() == HttpStatus.OK) {
                        successCount.incrementAndGet();
                    } else {
                        failCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        // NO SUNG!
        latch.countDown();

        // Cho 10 giay (nhieu luong hon nen can cho lau hon)
        Thread.sleep(10000);

        BigDecimal finalBalance = getBalanceFromDB();
        System.out.println("====== KET QUA TEST 5: RACE CONDITION NHIEU LUONG ======");
        System.out.println("So luong request thanh cong: " + successCount.get());
        System.out.println("So luong request that bai: " + failCount.get());
        System.out.println("So du cuoi cung: " + finalBalance);

        // Assert: KHONG DUOC CO LUONG NAO THANH CONG SE DAN DEN SO DU AM
        assertTrue(finalBalance.compareTo(BigDecimal.ZERO) >= 0,
                "So du KHONG DUOC AM! Current: " + finalBalance);

        // Assert: Tong tien rut duoc <= 50,000 (so tien ban dau)
        BigDecimal totalWithdrawn = new BigDecimal("50000").subtract(finalBalance);
        assertTrue(totalWithdrawn.compareTo(new BigDecimal("50000")) <= 0,
                "Tong tien rut duoc khong duoc vuot qua 50,000!");

        service.shutdown();
    }
}