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
@Table(name = "landed_costs", indexes = {
    @Index(name = "idx_landed_cost_company", columnList = "company_id"),
    @Index(name = "idx_landed_cost_shipment", columnList = "shipment_id"),
    @Index(name = "idx_landed_cost_created_at", columnList = "created_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class LandedCost {

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

    @Column(name = "calculation_name")
    private String calculationName;

    @Column(name = "origin_country", nullable = false, length = 3)
    private String originCountry;

    @Column(name = "destination_country", nullable = false, length = 3)
    private String destinationCountry;

    @Column(nullable = false, length = 5)
    @Builder.Default
    private String incoterm = "FOB";

    @Column(name = "hs_code", length = 10)
    private String hsCode;

    @Column(name = "transport_mode", length = 10)
    @Builder.Default
    private String transportMode = "SEA";

    @Column(name = "product_value", nullable = false)
    private BigDecimal productValue;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "EUR";

    @Column(name = "freight_cost")
    @Builder.Default
    private BigDecimal freightCost = BigDecimal.ZERO;

    @Column(name = "insurance_cost")
    @Builder.Default
    private BigDecimal insuranceCost = BigDecimal.ZERO;

    @Column(name = "duty_amount")
    @Builder.Default
    private BigDecimal dutyAmount = BigDecimal.ZERO;

    @Column(name = "duty_rate")
    @Builder.Default
    private BigDecimal dutyRate = BigDecimal.ZERO;

    @Column(name = "vat_amount")
    @Builder.Default
    private BigDecimal vatAmount = BigDecimal.ZERO;

    @Column(name = "vat_rate")
    @Builder.Default
    private BigDecimal vatRate = BigDecimal.ZERO;

    @Column(name = "port_charges")
    @Builder.Default
    private BigDecimal portCharges = BigDecimal.ZERO;

    @Column(name = "customs_fees")
    @Builder.Default
    private BigDecimal customsFees = BigDecimal.ZERO;

    @Column(name = "handling_fees")
    @Builder.Default
    private BigDecimal handlingFees = BigDecimal.ZERO;

    @Column(name = "last_mile_cost")
    @Builder.Default
    private BigDecimal lastMileCost = BigDecimal.ZERO;

    @Column(name = "total_landed_cost")
    @Builder.Default
    private BigDecimal totalLandedCost = BigDecimal.ZERO;

    @Column(name = "unit_count")
    @Builder.Default
    private Integer unitCount = 1;

    @Column(name = "total_landed_cost_per_unit")
    @Builder.Default
    private BigDecimal totalLandedCostPerUnit = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal margin;

    @Column(name = "margin_percent", precision = 5, scale = 2)
    private BigDecimal marginPercent;

    @Column(name = "selling_price", precision = 12, scale = 2)
    private BigDecimal sellingPrice;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "share_token", unique = true, length = 64)
    @JsonIgnore
    private String shareToken;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
