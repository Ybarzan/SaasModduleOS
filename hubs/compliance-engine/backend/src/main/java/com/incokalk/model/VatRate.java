package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "vat_rates", indexes = {
    @Index(name = "idx_vat_country", columnList = "country_code")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class VatRate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_type", nullable = false)
    private RateType rateType;

    @Column(name = "rate", nullable = false)
    private Double rate;

    @Column(name = "description", length = 200)
    private String description;

    @Column(name = "hs_chapters", length = 1000)
    private String hsChapters;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "valid_from", nullable = false)
    @Builder.Default
    private LocalDate validFrom = LocalDate.now();

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "margin_rate")
    private Double marginRate;

    @Column(name = "applies_to", length = 50)
    private String appliesTo; // GOODS, SERVICES, BOTH

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum RateType {
        STANDARD,
        REDUCED,
        SUPER_REDUCED,
        ZERO,
        MARGIN
    }
}
