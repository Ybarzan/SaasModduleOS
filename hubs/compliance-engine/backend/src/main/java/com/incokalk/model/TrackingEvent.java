package com.incokalk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tracking_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TrackingEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id", nullable = false)
    private ShipmentOrder shipment;

    @Column(nullable = false, length = 50)
    private String status;

    @Column(length = 500)
    private String location;

    private Double latitude;

    private Double longitude;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "event_time")
    @Builder.Default
    private LocalDateTime eventTime = LocalDateTime.now();

    @Column(length = 100)
    private String source;

    /** LIVE = venu d'un provider externe verifie (AviationStack/VesselAPI/Ship24) ou
     * d'un webhook transporteur entrant. MANUAL = saisi a la main par un utilisateur
     * via le changement de statut. Pas de valeur "SIMULATED" ici : contrairement aux
     * adaptateurs de reservation transporteur, aucun provider de tracking ne fabrique
     * de fausses donnees -- ils renvoient une liste vide en cas d'echec/absence de cle. */
    @Enumerated(EnumType.STRING)
    @Column(name = "data_source", nullable = false, length = 10)
    @Builder.Default
    private DataSource dataSource = DataSource.MANUAL;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum DataSource {
        LIVE,
        MANUAL
    }
}
