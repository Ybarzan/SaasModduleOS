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
@Table(name = "shipment_financials", indexes = {
    @Index(name = "idx_shipment_financials_company", columnList = "company_id"),
    @Index(name = "idx_shipment_financials_shipment", columnList = "shipment_id", unique = true),
    @Index(name = "idx_shipment_financials_lane", columnList = "origin, destination")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ShipmentFinancials {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false, unique = true)
    @JsonIgnore
    private ShipmentOrder shipment;

    @Column(name = "client_name")
    private String clientName;

    private String origin;

    private String destination;

    @Column(length = 10)
    private String mode;

    @Column(name = "carrier_name")
    private String carrierName;

    @Column(nullable = false, precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal revenue = BigDecimal.ZERO;

    @Column(name = "revenue_currency", length = 3)
    @Builder.Default
    private String revenueCurrency = "EUR";

    @Column(name = "cost_freight", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal costFreight = BigDecimal.ZERO;

    @Column(name = "cost_fuel", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal costFuel = BigDecimal.ZERO;

    @Column(name = "cost_handling", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal costHandling = BigDecimal.ZERO;

    @Column(name = "cost_customs", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal costCustoms = BigDecimal.ZERO;

    @Column(name = "cost_insurance", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal costInsurance = BigDecimal.ZERO;

    @Column(name = "cost_warehouse", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal costWarehouse = BigDecimal.ZERO;

    @Column(name = "cost_last_mile", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal costLastMile = BigDecimal.ZERO;

    @Column(name = "cost_other", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal costOther = BigDecimal.ZERO;

    @Column(name = "total_cost", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal totalCost = BigDecimal.ZERO;

    @Column(name = "gross_margin", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal grossMargin = BigDecimal.ZERO;

    @Column(name = "gross_margin_percent", precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal grossMarginPercent = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
