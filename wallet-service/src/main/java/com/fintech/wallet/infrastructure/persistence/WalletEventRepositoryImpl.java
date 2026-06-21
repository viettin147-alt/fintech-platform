package com.fintech.wallet.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fintech.wallet.domain.events.DomainEvent;
import com.fintech.wallet.domain.events.MoneyDepositedEvent;
import com.fintech.wallet.domain.events.MoneyWithdrawnEvent;
import com.fintech.wallet.domain.exceptions.OptimisticLockException;
import com.fintech.wallet.domain.repository.WalletEventRepository;
import com.fintech.wallet.infrastructure.JpaEventStoreEntity;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class WalletEventRepositoryImpl implements WalletEventRepository {
    private final SpringDataEventStoreRepository jpaRepository;
    private final ObjectMapper objectMapper; // Dùng thư viện Jackson để parse JSON

    public WalletEventRepositoryImpl(SpringDataEventStoreRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule()); // Hỗ trợ Java 8 LocalDateTime
    }

        @Override
    public void saveEvents(List<DomainEvent> events) {
        for (DomainEvent event : events) {
            try {
                String jsonPayload = objectMapper.writeValueAsString(event);
                JpaEventStoreEntity entity = new JpaEventStoreEntity(
                        event.aggregateId(),
                        event.version(),
                        event.getClass().getSimpleName(),
                        jsonPayload,
                        event.occurredOn()
                );
                    jpaRepository.saveAndFlush(entity);
            } catch (DataIntegrityViolationException e) {
                // UniqueConstraint(aggregate_id, version) bi trung -> Race Condition detected!
                throw new OptimisticLockException(
                        "Xung dot phien ban! AggregateId=" + event.aggregateId() + ", version=" + event.version(), e);
            } catch (Exception e) {
                throw new RuntimeException("Loi luu tru Event sang JSON", e);
            }
        }
    }

    @Override
    public List<DomainEvent> loadEvents(String aggregateId) {
        List<JpaEventStoreEntity> entities = jpaRepository.findByAggregateIdOrderByOccurredOnAsc(aggregateId);
        List<DomainEvent> history = new ArrayList<>();

        for (JpaEventStoreEntity entity : entities) {
            try {
                // Dựa vào tên EventType để phục hồi đúng Class Object ban đầu
                if ("MoneyDepositedEvent".equals(entity.getEventType())) {
                    DomainEvent event = objectMapper.readValue(entity.getPayload(), MoneyDepositedEvent.class);
                    history.add(event);
                }
                else if ("MoneyWithdrawnEvent".equals(entity.getEventType())) {
                    DomainEvent event = objectMapper.readValue(entity.getPayload(), MoneyWithdrawnEvent.class);
                    history.add(event);
                }
            } catch (Exception e) {
                throw new RuntimeException("Lỗi phục hồi Event từ JSON", e);
            }
        }
        return history;
    }
    @Override
    public void clearAllEvents() {
        jpaRepository.deleteAllInBatch();
    }

}
