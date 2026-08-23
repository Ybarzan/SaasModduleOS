package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "cargo_insurance_quotes", indexes = {
    @Index(name = "idx_cargo_insurance_quotes_company", columnList = "company_id"),
    @Index(name = "idx_cargo_insurance_quotes_status", columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CargoInsuranceQuote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "goods_value", nullable = false, precision = 15, scale = 2)
    private BigDecimal goodsValue;

    @Column(name = "weight_kg", precision = 12, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "transport_mode", length = 10)
    private String transportMode;

    @Column(name = "goods_category", length = 30)
    private String goodsCategory;

    @Column(name = "origin_country", length = 3)
    private String originCountry;

    @Column(name = "destination_country", length = 3)
    private String destinationCountry;

    @Column(length = 3)
    @Builder.Default
    private String currency = "EUR";

    @Column(name = "premium_rate", nullable = false, precision = 10, scale = 6)
    private BigDecimal premiumRate;

    @Column(name = "premium_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal premiumAmount;

    @Column(name = "coverage_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal coverageAmount;

    @Column(name = "coverage_type", length = 120)
    private String coverageType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.QUOTE;

    @Column(name = "policy_number", length = 40)
    private String policyNumber;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum Status {
        QUOTE, POLICY
    }
}
