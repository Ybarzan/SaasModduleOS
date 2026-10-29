package com.fleethub.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Journal d'événements append-only par expédition -- ADR-0001
 * (docs/adr/0001-event-store-canonique.md), Option C scope fleet-hub.
 * Complète {@link TrackingEvent} (jalons statut/position) plutôt que de le
 * remplacer : couvre aussi la création, l'assignation de navire et la
 * capture d'ETA, pour une timeline unifiée exploitable par une future
 * projection. Jamais mis à jour après écriture ; {@code recordedAt} fige le
 * moment de l'écriture elle-même, {@code occurredAt} celui de l'événement
 * métier (identiques en pratique aujourd'hui, mais conceptuellement
 * distincts si une relecture/rejouabilité différée apparaît plus tard).
 */
@Entity
@Table(name = "shipment_events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ShipmentEvent {

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

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private EventType eventType;

    /** Sérialisé en JSON par {@code ShipmentEventRecorder} -- pas de colonne
     * JSONB typée : aucun précédent dans le projet, évite une dépendance
     * (hypersistence-utils) pour un seul usage write-only en Phase 1. */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @CreationTimestamp
    @Column(name = "recorded_at", updatable = false)
    private LocalDateTime recordedAt;

    public enum EventType {
        SHIPMENT_CREATED,
        STATUS_CHANGED,
        VESSEL_ASSIGNED,
        ETA_CAPTURED
    }
}
