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
@Table(name = "carbon_offsets", indexes = {
    @Index(name = "idx_carbon_offset_company", columnList = "company_id"),
    @Index(name = "idx_carbon_offset_shipment", columnList = "shipment_id"),
    @Index(name = "idx_carbon_offset_status", columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CarbonOffset {

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

    @Column(name = "co2_emissions_kg", nullable = false, precision = 15, scale = 2)
    private BigDecimal co2EmissionsKg;

    @Column(name = "offset_credits_purchased", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal offsetCreditsPurchased = BigDecimal.ZERO;

    @Column(name = "offset_credits_retired", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal offsetCreditsRetired = BigDecimal.ZERO;

    @Column(name = "offset_provider")
    private String offsetProvider;

    @Column(name = "offset_project_name")
    private String offsetProjectName;

    @Column(name = "offset_project_type")
    private String offsetProjectType;

    @Column(name = "offset_cost_per_ton", precision = 15, scale = 2)
    private BigDecimal offsetCostPerTon;

    @Column(name = "offset_total_cost", precision = 15, scale = 2)
    private BigDecimal offsetTotalCost;

    @Column(name = "offset_currency", length = 3)
    @Builder.Default
    private String offsetCurrency = "EUR";

    @Column(name = "certification_id")
    private String certificationId;

    @Column(name = "retired_at")
    private LocalDateTime retiredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private OffsetStatus status = OffsetStatus.TRACKING;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum OffsetStatus {
        TRACKING, CREDITS_PURCHASED, OFFSETTED, PARTIAL
    }
}
