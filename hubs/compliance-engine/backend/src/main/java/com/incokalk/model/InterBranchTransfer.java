package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "inter_branch_transfers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InterBranchTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "from_branch_id", nullable = false)
    private UUID fromBranchId;

    @Column(name = "to_branch_id", nullable = false)
    private UUID toBranchId;

    @Column(name = "goods_description", columnDefinition = "TEXT")
    private String goodsDescription;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal quantity;

    @Column(length = 50)
    @Builder.Default
    private String unit = "UNIT";

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private TransferStatus status = TransferStatus.PENDING;

    @Column(name = "initiated_by")
    private UUID initiatedBy;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum TransferStatus {
        PENDING, IN_TRANSIT, COMPLETED, CANCELLED
    }
}
