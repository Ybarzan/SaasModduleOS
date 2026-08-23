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
@Table(name = "customs_declarations", indexes = {
    @Index(name = "idx_customs_declaration_company", columnList = "company_id"),
    @Index(name = "idx_customs_declaration_status", columnList = "status"),
    @Index(name = "idx_customs_declaration_number", columnList = "declaration_number"),
    @Index(name = "idx_customs_declaration_shipment", columnList = "shipment_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CustomsDeclaration {

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

    @Column(name = "declaration_number", nullable = false, unique = true, length = 50)
    private String declarationNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private DeclarationType declarationType = DeclarationType.DAU_IMPORT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private DeclarationStatus status = DeclarationStatus.DRAFT;

    @Column(name = "customs_office", length = 150)
    private String customsOffice;

    @Column(name = "customs_regime", length = 10)
    private String customsRegime;

    @Column(name = "customs_code", length = 20)
    private String customsCode;

    @Column(name = "declared_value")
    private BigDecimal declaredValue;

    @Column(length = 3)
    @Builder.Default
    private String currency = "EUR";

    @Column(name = "origin_country", length = 2)
    private String originCountry;

    @Column(name = "destination_country", length = 2)
    private String destinationCountry;

    @Column(name = "hs_code", length = 10)
    private String hsCode;

    @Column(name = "goods_description", columnDefinition = "TEXT")
    private String goodsDescription;

    @Column(name = "net_weight")
    private BigDecimal netWeight;

    @Column(name = "gross_weight")
    private BigDecimal grossWeight;

    @Column(name = "packages")
    private Integer packages;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "eori_id")
    @JsonIgnore
    private EoriNumber eori;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "cleared_at")
    private LocalDateTime clearedAt;

    @Column(name = "rejected_at")
    private LocalDateTime rejectedAt;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum DeclarationType {
        DAU_IMPORT, DAU_EXPORT, TRANSIT_T1, TRANSIT_T2
    }

    public enum DeclarationStatus {
        DRAFT, SUBMITTED, UNDER_REVIEW, CLEARED, RELEASED, REJECTED
    }
}
