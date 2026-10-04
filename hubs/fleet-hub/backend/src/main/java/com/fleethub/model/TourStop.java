package com.fleethub.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Arrêt d'une tournée sur un site, avec son créneau horaire, son heure
 * d'arrivée estimée et la preuve de passage (signataire, colis, échantillons,
 * température relevée pour la collecte santé).
 */
@Entity
@Table(name = "tour_stop")
@Getter
@Setter
@NoArgsConstructor
public class TourStop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tour_id", nullable = false)
    private Tour tour;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    /** Ordre de passage (1 = premier arrêt). */
    @Column(name = "stop_sequence", nullable = false)
    private int sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_type", nullable = false, length = 32)
    private StopType type = StopType.LIVRAISON;

    private LocalTime windowStart;
    private LocalTime windowEnd;

    @Column(nullable = false)
    private int serviceMinutes = 5;

    /** Arrivée estimée par le calcul d'itinéraire. */
    private LocalTime plannedArrival;

    /** Retard estimé sur la fin de créneau (minutes, 0 si dans le créneau). */
    private Integer plannedLatenessMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StopStatus status = StopStatus.A_FAIRE;

    private LocalDateTime arrivedAt;
    private LocalDateTime completedAt;

    // ---- Preuve de passage ----
    private String signedBy;
    private Integer parcelCount;
    /** Quantité attendue (colis / sachets), reprise de la commande : pré-remplit la saisie du chauffeur. */
    private Integer expectedQuantity;
    /** Nombre d'échantillons / sachets remis ou collectés (collecte santé). */
    private Integer sampleCount;
    /** Température relevée au passage (°C, chaîne du froid). */
    private Double temperatureCelsius;
    /** Plage de température exigée (ex. 2–8 °C réfrigéré, 15–25 °C ambiant). Null = pas de contrôle. */
    private Double temperatureMinCelsius;
    private Double temperatureMaxCelsius;
    /** Codes scannés (sachets, colis), séparés par des virgules. */
    @Column(length = 2000)
    private String scannedCodes;
    private String failureReason;

    @Column(length = 1000)
    private String notes;

    public enum StopType { LIVRAISON, ENLEVEMENT, COLLECTE }

    public enum StopStatus { A_FAIRE, FAIT, ECHEC }

    /** Température relevée hors de la plage exigée (rupture de la chaîne du froid). */
    public boolean isTemperatureExcursion() {
        if (temperatureCelsius == null) {
            return false;
        }
        return (temperatureMinCelsius != null && temperatureCelsius < temperatureMinCelsius)
                || (temperatureMaxCelsius != null && temperatureCelsius > temperatureMaxCelsius);
    }

    /** Arrivée réelle dans le créneau (null si pas de créneau ou pas encore fait). */
    public Boolean deliveredOnTime() {
        if (completedAt == null || windowEnd == null) {
            return null;
        }
        LocalDateTime reference = arrivedAt != null ? arrivedAt : completedAt;
        return !reference.toLocalTime().isAfter(windowEnd);
    }
}
