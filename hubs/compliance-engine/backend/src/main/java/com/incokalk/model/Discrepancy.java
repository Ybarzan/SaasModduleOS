package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "discrepancies", indexes = {
    @Index(name = "idx_discrepancies_company", columnList = "company_id"),
    @Index(name = "idx_discrepancies_order", columnList = "receiving_order_id"),
    @Index(name = "idx_discrepancies_status", columnList = "resolution_status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Discrepancy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "receiving_order_id", nullable = false)
    private UUID receivingOrderId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "line_id")
    private UUID lineId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(name = "expected_qty", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal expectedQty = BigDecimal.ZERO;

    @Column(name = "actual_qty", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal actualQty = BigDecimal.ZERO;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal difference = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_status", length = 20)
    @Builder.Default
    private ResolutionStatus resolutionStatus = ResolutionStatus.OPEN;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Type {
        OVER, SHORT, DAMAGED, UNEXPECTED
    }

    public enum ResolutionStatus {
        OPEN, RESOLVED, CANCELLED
    }
}
