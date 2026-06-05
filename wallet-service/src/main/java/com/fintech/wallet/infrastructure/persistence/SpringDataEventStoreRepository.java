package com.fintech.wallet.infrastructure.persistence;

import com.fintech.wallet.infrastructure.JpaEventStoreEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SpringDataEventStoreRepository extends JpaRepository<JpaEventStoreEntity, Long> {
    List<JpaEventStoreEntity> findByAggregateIdOrderByOccurredOnAsc(String aggregateId);
}
