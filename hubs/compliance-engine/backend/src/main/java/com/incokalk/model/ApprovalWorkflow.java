package com.incokalk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "approval_workflows", indexes = {
        @Index(name = "idx_approval_workflow_company", columnList = "company_id"),
        @Index(name = "idx_approval_workflow_entity_type", columnList = "entity_type")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApprovalWorkflow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false)
    private EntityType entityType;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "threshold_amount")
    private BigDecimal thresholdAmount;

    @Column(name = "threshold_currency")
    @Builder.Default
    private String thresholdCurrency = "EUR";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum EntityType {
        QUOTE, CARRIER_INVOICE, PURCHASE_ORDER, EXPENSE_REPORT, CUSTOM
    }
}
