package com.fintech.wallet.domain.models;

import java.util.UUID;

public record WalletId(String value) {
    public static WalletId generate() {
        return new WalletId(UUID.randomUUID().toString());
    }
}