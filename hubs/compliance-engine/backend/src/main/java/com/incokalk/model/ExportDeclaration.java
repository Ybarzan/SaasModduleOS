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
@Table(name = "export_declarations", indexes = {
        @Index(name = "idx_export_company", columnList = "company_id"),
        @Index(name = "idx_export_status", columnList = "status"),
        @Index(name = "idx_export_shipment", columnList = "shipment_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ExportDeclaration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id")
    @JsonIgnore
    private ShipmentOrder shipment;

    @Column(name = "declaration_number")
    private String declarationNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private ExportType declarationType = ExportType.AES;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ExportStatus status = ExportStatus.DRAFT;

    @Column(name = "exporter_eori", length = 20)
    private String exporterEori;

    @Column(name = "destination_country", length = 2)
    private String destinationCountry;

    @Column(name = "goods_description", columnDefinition = "TEXT")
    private String goodsDescription;

    @Column(name = "hs_code", length = 10)
    private String hsCode;

    @Column(name = "declared_value")
    private BigDecimal declaredValue;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "EUR";

    @Column(name = "net_weight")
    private BigDecimal netWeight;

    @Column(name = "gross_weight")
    private BigDecimal grossWeight;

    @Column(name = "packages_count")
    private Integer packagesCount;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "validated_at")
    private LocalDateTime validatedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum ExportType {
        AES, EXS
    }

    public enum ExportStatus {
        DRAFT, SUBMITTED, VALIDATED, REJECTED
    }
}
