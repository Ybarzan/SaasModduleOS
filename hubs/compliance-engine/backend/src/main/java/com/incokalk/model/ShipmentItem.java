package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipment_items", indexes = {
    @Index(name = "idx_shipment_items_company", columnList = "company_id"),
    @Index(name = "idx_shipment_items_shipment", columnList = "shipment_id"),
    @Index(name = "idx_shipment_items_item", columnList = "item_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ShipmentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "shipment_id", nullable = false)
    private UUID shipmentId;

    @Column(name = "item_id")
    private UUID itemId;

    @Column(length = 100)
    private String sku;

    @Column(length = 300)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "hs_code", length = 20)
    private String hsCode;

    @Column(length = 3)
    private String originCountry;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(length = 10)
    @Builder.Default
    private String unit = "PCS";

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}