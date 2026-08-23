package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "receiving_scans", indexes = {
    @Index(name = "idx_receiving_scans_order", columnList = "receiving_order_id"),
    @Index(name = "idx_receiving_scans_item", columnList = "item_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReceivingScan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "receiving_order_id", nullable = false)
    private UUID receivingOrderId;

    @Column(name = "line_id")
    private UUID lineId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(length = 200)
    private String barcode;

    @Column(precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal quantity = BigDecimal.ONE;

    @Column(name = "lot_number", length = 100)
    private String lotNumber;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "serial_number", length = 100)
    private String serialNumber;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "scanned_by")
    private UUID scannedBy;

    @Column(name = "scanned_at")
    @Builder.Default
    private LocalDateTime scannedAt = LocalDateTime.now();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
