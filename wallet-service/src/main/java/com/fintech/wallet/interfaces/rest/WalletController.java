package com.fintech.wallet.interfaces.rest;

import com.fintech.wallet.application.WalletApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/wallets")
public class WalletController {

    private final WalletApplicationService walletApplicationService;

    public WalletController(WalletApplicationService walletApplicationService) {
        this.walletApplicationService = walletApplicationService;
    }

    // API nạp tiền vào ví
    @PostMapping("/{id}/deposit")
    public ResponseEntity<String> deposit(@PathVariable String id, @RequestParam BigDecimal amount) {
        try {
            walletApplicationService.depositMoney(id, amount);
            return ResponseEntity.ok("Nạp tiền thành công vào ví: " + id);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Lỗi: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/withdraw")
    public ResponseEntity<String> withdraw(@PathVariable String id, @RequestParam BigDecimal amount) {
        try {
            walletApplicationService.withdrawMoney(id, amount);
            return ResponseEntity.ok("Rút tiền thành công từ ví: " + id);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Giao dịch thất bại: " + e.getMessage());
        }
    }
}