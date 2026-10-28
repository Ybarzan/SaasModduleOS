package com.fleethub.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Fiabilité mesurée d'une source d'ETA pour un groupe transporteur x lane x
 * mode -- une ligne par groupe, réécrite à chaque recalcul
 * ({@code EtaSourceReliabilityService}), jamais un historique de runs (voir
 * docs/03-plan-migration.md Phase 2 J3-J4).
 * <p>
 * {@code meanDeviationDays} (signé, biais moyen : positif = arrivées en
 * retard par rapport à l'ETA annoncée) et {@code meanAbsoluteDeviationDays}
 * (non signé, amplitude moyenne de l'erreur) sont calculés sur
 * {@code sampleCount} observations réelles -- jamais sur une estimation.
 */
@Entity
@Table(name = "eta_source_reliability")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EtaSourceReliability {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrier_id")
    @JsonIgnore
    private Carrier carrier;

    @JsonProperty("carrierId")
    public UUID getCarrierIdValue() {
        return carrier != null ? carrier.getId() : null;
    }

    @Column(name = "origin_country", nullable = false, length = 3)
    private String originCountry;

    @Column(name = "destination_country", nullable = false, length = 3)
    private String destinationCountry;

    @Column(name = "transport_mode", nullable = false, length = 20)
    private String transportMode;

    @Column(name = "sample_count", nullable = false)
    @Builder.Default
    private Integer sampleCount = 0;

    @Column(name = "mean_deviation_days", precision = 8, scale = 2)
    private BigDecimal meanDeviationDays;

    @Column(name = "mean_absolute_deviation_days", precision = 8, scale = 2)
    private BigDecimal meanAbsoluteDeviationDays;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
