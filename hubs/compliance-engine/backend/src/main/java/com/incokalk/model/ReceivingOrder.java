package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "receiving_orders", indexes = {
    @Index(name = "idx_receiving_orders_company", columnList = "company_id"),
    @Index(name = "idx_receiving_orders_warehouse", columnList = "warehouse_id"),
    @Index(name = "idx_receiving_orders_shipment", columnList = "shipment_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReceivingOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "shipment_id")
    private UUID shipmentId;

    @Column(name = "order_number", nullable = false, length = 50)
    private String orderNumber;

    @Column(length = 200)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.DRAFT;

    @Column(name = "received_by")
    private UUID receivedBy;

    @Column(name = "received_at")
    private LocalDateTime receivedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_by")
    private UUID createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Status {
        DRAFT, RECEIVING, COMPLETED, CANCELLED
    }
}
