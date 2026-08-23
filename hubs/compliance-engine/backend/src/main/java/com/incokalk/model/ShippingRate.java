package com.incokalk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipping_rates")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ShippingRate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrier_id", nullable = false)
    @JsonIgnore
    private Carrier carrier;

    @com.fasterxml.jackson.annotation.JsonProperty("carrierId")
    public UUID getCarrierId() {
        return carrier != null ? carrier.getId() : null;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("carrierName")
    public String getCarrierName() {
        return carrier != null ? carrier.getName() : null;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("carrierCode")
    public String getCarrierCode() {
        return carrier != null ? carrier.getCode() : null;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    @JsonIgnore
    private Company company;

    @Column(nullable = false)
    private String name;

    @Column(name = "origin_country", nullable = false, length = 3)
    private String originCountry;

    @Column(name = "destination_country", nullable = false, length = 3)
    private String destinationCountry;

    @Column(name = "transport_mode", nullable = false, length = 20)
    private String transportMode;

    @Column(name = "min_weight_kg")
    private Double minWeightKg;

    @Column(name = "max_weight_kg")
    private Double maxWeightKg;

    @Column(name = "base_rate", nullable = false)
    private double baseRate;

    @Column(length = 3)
    @Builder.Default
    private String currency = "EUR";

    @Column(name = "rate_per_kg")
    @Builder.Default
    private double ratePerKg = 0;

    @Column(name = "rate_per_cbm")
    @Builder.Default
    private double ratePerCbm = 0;

    @Column(name = "transit_days_min")
    private Integer transitDaysMin;

    @Column(name = "transit_days_max")
    private Integer transitDaysMax;

    @Column(name = "co2_estimate_kg")
    private Double co2EstimateKg;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "valid_from")
    private LocalDateTime validFrom;

    @Column(name = "valid_until")
    private LocalDateTime validUntil;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
