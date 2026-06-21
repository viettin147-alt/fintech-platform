package com.fintech.wallet.domain.repository;

import com.fintech.wallet.domain.events.DomainEvent;
import org.springframework.stereotype.Repository;

import java.util.List;


public interface WalletEventRepository {
    void saveEvents(List<DomainEvent> events);
    List<DomainEvent> loadEvents(String aggregateId);
    void clearAllEvents();
}
