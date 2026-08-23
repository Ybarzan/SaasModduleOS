package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ecommerce_sync_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ECommerceSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "integration_id", nullable = false)
    private UUID integrationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SyncStatus status;

    @Column(name = "orders_processed")
    @Builder.Default
    private Integer ordersProcessed = 0;

    @Column(name = "orders_created")
    @Builder.Default
    private Integer ordersCreated = 0;

    @Column(name = "orders_failed")
    @Builder.Default
    private Integer ordersFailed = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "started_at", updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public enum SyncStatus {
        SUCCESS, PARTIAL, FAILED
    }
}
