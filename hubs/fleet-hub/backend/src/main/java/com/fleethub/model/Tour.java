package com.fleethub.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Tournée : une journée de passages ordonnés (livraisons, enlèvements,
 * collectes d'échantillons) réalisée par un chauffeur avec un véhicule,
 * au départ et au retour d'un dépôt.
 */
@Entity
@Table(name = "tour")
@Getter
@Setter
@NoArgsConstructor
public class Tour {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String name;

    @Column(name = "tour_date", nullable = false)
    private LocalDate date;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "truck_id")
    private Truck truck;

    /** Point de départ et de retour (optionnel). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "depot_id")
    private Site depot;

    @Column(nullable = false)
    private LocalTime plannedStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private TourStatus status = TourStatus.PLANIFIEE;

    /** Estimations issues du dernier calcul d'itinéraire. */
    private Double plannedDistanceKm;
    private Integer plannedDurationMinutes;
    private LocalDateTime optimizedAt;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    @Column(length = 1000)
    private String notes;

    @OneToMany(mappedBy = "tour", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<TourStop> stops = new ArrayList<>();

    public enum TourStatus { PLANIFIEE, EN_COURS, TERMINEE, ANNULEE }

    public boolean isEditable() {
        return status == TourStatus.PLANIFIEE || status == TourStatus.EN_COURS;
    }
}
