package com.fintech.wallet.infrastructure;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "event_store", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"aggregate_id", "version"})
})
public class JpaEventStoreEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId; // Ví dụ: ID của ví (wallet-001)

    @Column(name = "version", nullable = false)
    private Long version; // [MỚI CHÈN] Cột lưu số phiên bản vào DB

    @Column(name = "event_type", nullable = false)
    private String eventType;   // Ví dụ: "MoneyDepositedEvent"

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false)
    private String payload;     // Dữ liệu Event được Convert thành chuỗi JSON

    @Column(name = "occurred_on", nullable = false)
    private LocalDateTime occurredOn;

    public JpaEventStoreEntity() {}

    public JpaEventStoreEntity(String aggregateId, Long version, String eventType, String payload, LocalDateTime occurredOn) {
        this.aggregateId = aggregateId;
        this.version = version;
        this.eventType = eventType;
        this.payload = payload;
        this.occurredOn = occurredOn;
    }

    public String getAggregateId() { return aggregateId; }
    public Long getVersion() { return version; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public LocalDateTime getOccurredOn() { return occurredOn; }
}
