package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "receiving_order_lines", indexes = {
    @Index(name = "idx_receiving_lines_order", columnList = "receiving_order_id"),
    @Index(name = "idx_receiving_lines_item", columnList = "item_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReceivingOrderLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "receiving_order_id", nullable = false)
    private UUID receivingOrderId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(name = "quantity_expected", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal quantityExpected = BigDecimal.ZERO;

    @Column(name = "quantity_received", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal quantityReceived = BigDecimal.ZERO;

    @Column(name = "quantity_damaged", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal quantityDamaged = BigDecimal.ZERO;

    @Column(length = 10)
    @Builder.Default
    private String unit = "PCS";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
