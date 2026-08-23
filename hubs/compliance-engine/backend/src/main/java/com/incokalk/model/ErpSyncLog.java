package com.incokalk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "erp_sync_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ErpSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "erp_config_id", nullable = false)
    @JsonIgnore
    private ErpConfig erpConfig;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    @Column(name = "sync_type", nullable = false, length = 50)
    private String syncType;

    @Column(name = "direction", nullable = false, length = 10)
    private String direction;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "records_total")
    @Builder.Default
    private Integer recordsTotal = 0;

    @Column(name = "records_synced")
    @Builder.Default
    private Integer recordsSynced = 0;

    @Column(name = "records_failed")
    @Builder.Default
    private Integer recordsFailed = 0;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "started_at", updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
