package com.fleethub.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Prédiction ETA (temps d'arrivée) pour une expédition. Porté depuis compliance-engine. */
@Entity
@Table(name = "eta_predictions", indexes = {
    @Index(name = "idx_eta_company", columnList = "company_id"),
    @Index(name = "idx_eta_shipment", columnList = "shipment_id"),
    @Index(name = "idx_eta_lane", columnList = "origin, destination"),
    @Index(name = "idx_eta_mode", columnList = "mode")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EtaPrediction {

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

    @Column(nullable = false)
    private String origin;

    @Column(nullable = false)
    private String destination;

    @Column(nullable = false, length = 10)
    private String mode;

    @Column(name = "carrier_name")
    private String carrierName;

    @Column(name = "predicted_arrival", nullable = false)
    private LocalDateTime predictedArrival;

    @Column(name = "confidence_percent", precision = 5, scale = 2)
    private BigDecimal confidencePercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_level", nullable = false, length = 10)
    @Builder.Default
    private ConfidenceLevel confidenceLevel = ConfidenceLevel.MEDIUM;

    @Column(name = "baseline_days")
    private Integer baselineDays;

    @Column(name = "predicted_days")
    private Integer predictedDays;

    @Column(name = "carrier_estimate_days")
    private Integer carrierEstimateDays;

    @Column(name = "variance_days")
    private Integer varianceDays;

    @Column(name = "risk_factors", columnDefinition = "TEXT")
    private String riskFactors;

    @Column(name = "seasonal_factor", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal seasonalFactor = BigDecimal.ONE;

    @Column(name = "congestion_factor", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal congestionFactor = BigDecimal.ONE;

    @Column(name = "customs_delay_days")
    @Builder.Default
    private Integer customsDelayDays = 0;

    @Column(name = "weather_delay_days")
    @Builder.Default
    private Integer weatherDelayDays = 0;

    /** true seulement quand EtaPredictionService.applyReliabilityWeighting() a
     * réellement mélangé un score de fiabilité mesuré (EtaSourceReliabilityService) --
     * jamais sur un repli heuristique, même si confidencePercent est identique
     * dans les deux cas. Transparence du score, docs/03-plan-migration.md Phase 2. */
    @Column(name = "reliability_weighted", nullable = false)
    @Builder.Default
    private boolean reliabilityWeighted = false;

    /** Nombre d'observations réelles ayant backé le blend -- renseigné
     * uniquement quand reliabilityWeighted est true. */
    @Column(name = "reliability_sample_count")
    private Integer reliabilitySampleCount;

    @Column(name = "is_on_time")
    private Boolean isOnTime;

    @Column(name = "actual_arrival")
    private LocalDateTime actualArrival;

    @Column(name = "actual_days")
    private Integer actualDays;

    @Column(name = "prediction_accuracy", precision = 8, scale = 2)
    private BigDecimal predictionAccuracy;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum ConfidenceLevel {
        HIGH, MEDIUM, LOW
    }
}
