package com.fleethub.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Une ETA annoncée par une source externe pour une expédition réelle, figée au
 * moment où elle a été émise -- jamais recalculée après coup -- afin de pouvoir
 * la comparer plus tard à {@link ShipmentOrder#getActualDeliveryDate()} et
 * mesurer la fiabilité réelle de chaque source (voir
 * {@code EtaSourceReliabilityService}, docs/03-plan-migration.md Phase 2 J1-J4).
 * <p>
 * {@code originCountry}/{@code destinationCountry}/{@code transportMode} sont
 * dupliqués depuis l'expédition au moment de l'observation (même principe que
 * {@code OrchestrationSuggestion.contextJson}) : si ces champs changent après
 * coup sur l'expédition, la fiabilité historique ne doit pas être réécrite.
 */
@Entity
@Table(name = "eta_reported_sources", indexes = {
    @Index(name = "idx_ers_company", columnList = "company_id"),
    @Index(name = "idx_ers_shipment", columnList = "shipment_id"),
    @Index(name = "idx_ers_reliability_group", columnList = "carrier_id, origin_country, destination_country, transport_mode")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EtaReportedSource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false)
    @JsonIgnore
    private ShipmentOrder shipment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrier_id")
    @JsonIgnore
    private Carrier carrier;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Source source;

    @Column(name = "reported_days")
    private Integer reportedDays;

    @Column(name = "reported_arrival")
    private LocalDateTime reportedArrival;

    @Column(name = "origin_country", length = 3)
    private String originCountry;

    @Column(name = "destination_country", length = 3)
    private String destinationCountry;

    @Column(name = "transport_mode", length = 20)
    private String transportMode;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    /** Seule source alimentée aujourd'hui -- voir le javadoc de classe pour
     *  pourquoi l'ETA dérivé AIS prévu par le plan n'est pas encore là. */
    public enum Source {
        CARRIER_BOOKING
    }
}
