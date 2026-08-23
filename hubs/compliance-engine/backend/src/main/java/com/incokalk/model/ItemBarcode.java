package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "item_barcodes", indexes = {
    @Index(name = "uq_item_barcodes_company_code", columnList = "company_id, barcode", unique = true),
    @Index(name = "idx_item_barcodes_item", columnList = "item_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ItemBarcode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "item_id", nullable = false)
    private UUID itemId;

    @Column(nullable = false, length = 200)
    private String barcode;

    @Column(name = "barcode_type", length = 20)
    @Builder.Default
    private String barcodeType = "EAN13";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
