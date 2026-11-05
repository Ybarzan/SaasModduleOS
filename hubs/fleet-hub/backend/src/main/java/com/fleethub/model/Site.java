package com.fleethub.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalTime;

/**
 * Lieu desservi par les tournées : client, pharmacie, laboratoire, dépôt…
 * Les coordonnées GPS servent à l'optimisation de l'ordre de passage.
 */
@Entity
@Table(name = "site")
@Getter
@Setter
@NoArgsConstructor
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SiteKind kind;

    /** Référence métier (code client, FINESS d'une pharmacie…). */
    private String reference;

    private String address;
    private String postalCode;
    private String city;

    private Double latitude;
    private Double longitude;

    private String contactName;
    private String contactPhone;

    /** Créneau d'ouverture par défaut (repris sur les arrêts). */
    private LocalTime openingFrom;
    private LocalTime openingTo;

    /** Durée moyenne d'un passage sur place, en minutes. */
    private Integer serviceMinutes;

    @Column(length = 1000)
    private String notes;

    @Column(nullable = false)
    private boolean active = true;

    public enum SiteKind { DEPOT, CLIENT, PHARMACIE, LABORATOIRE, ETABLISSEMENT_SANTE, AUTRE }

    public boolean hasCoordinates() {
        return latitude != null && longitude != null;
    }
}
