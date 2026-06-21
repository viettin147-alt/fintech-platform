package com.fintech.wallet.interfaces.rest;


import com.fintech.wallet.application.WalletApplicationService;
import com.fintech.wallet.domain.exceptions.InsufficientBalanceException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/wallets")
public class WalletController {

    private final WalletApplicationService walletApplicationService;

    public WalletController(WalletApplicationService walletApplicationService) {
        this.walletApplicationService = walletApplicationService;
    }

        // API nap tien vao vi
    @PostMapping("/{id}/deposit")
    public ResponseEntity<?> deposit(@PathVariable String id, @RequestParam BigDecimal amount) {
        try {
            walletApplicationService.depositMoney(id, amount);
            return ResponseEntity.ok(Map.of(
                    "message", "Nap tien thanh cong vao vi: " + id,
                    "walletId", id,
                    "amount", amount
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Loi nap tien",
                    "message", e.getMessage()
            ));
        }
    }

        @PostMapping("/{id}/withdraw")
    public ResponseEntity<?> withdraw(@PathVariable String id, @RequestParam BigDecimal amount) {
        try {
            walletApplicationService.withdrawMoney(id, amount);
            return ResponseEntity.ok(Map.of(
                    "message", "Rut tien thanh cong tu vi: " + id,
                    "walletId", id,
                    "amount", amount
            ));
        } catch (InsufficientBalanceException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "So du khong du",
                    "message", e.getMessage(),
                    "currentBalance", e.getCurrentBalance(),
                    "requestedAmount", e.getRequestedAmount()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "He thong dang ban, vui long thu lai sau!",
                    "message", e.getMessage()
            ));
        }
    }

    // API xem so du hien tai cua vi
    @GetMapping("/{id}/balance")
    public ResponseEntity<?> getBalance(@PathVariable String id) {
        try {
            BigDecimal balance = walletApplicationService.getBalance(id);
            return ResponseEntity.ok(Map.of(
                    "walletId", id,
                    "balance", balance
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Khong the lay so du",
                    "message", e.getMessage()
            ));
        }
    }
}