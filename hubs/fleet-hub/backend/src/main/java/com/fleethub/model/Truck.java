package com.fleethub.model;

import java.math.BigDecimal;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "truck", uniqueConstraints = @UniqueConstraint(
        name = "uk_truck_company_registration", columnNames = {"company_id", "registration"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Truck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String registration;

    @Column(nullable = false)
    private String brand;

    @Column(nullable = false)
    private String model;

    private Integer modelYear;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TruckType truckType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FuelType fuelType;

    private Double capacityTons;

    private LocalDate acquisitionDate;

    @Column(precision = 15, scale = 2)
    private BigDecimal purchasePrice;

    /**
     * Consommation de référence aux 100 km, dans l'unité d'énergie du véhicule : kWh pour
     * un électrique, litres sinon (le nom de la colonne date d'avant les électriques). Les
     * pleins ({@link FuelRecord#getLiters()}) suivent la même unité : une recharge se saisit en kWh.
     */
    private Double expectedConsumptionL100Km;

    private Double currentLatitude;
    private Double currentLongitude;
    private Double currentSpeedKph;

    @Enumerated(EnumType.STRING)
    private VehicleStatus currentStatus;

    private java.time.LocalDateTime lastGpsUpdate;

    @Column(nullable = false)
    private boolean active = true;

    /**
     * Équipement tachygraphe explicite. Null = déduit du type : les poids lourds
     * (tracteur, porteur) en sont toujours équipés ; un VUL 2,5–3,5 t ne l'est
     * qu'en transport international (Paquet Mobilité, depuis le 01/07/2026).
     */
    private Boolean tachographEquipped;

    public enum VehicleStatus {
        ROULAGE, ARRET, REPOS, ALERTE, IMMOBILISE
    }

    /** Catégorie du véhicule. FOURGON = porteur fourgon PL ; VUL = camionnette ≤ 3,5 t ; VL = véhicule léger. */
    public enum TruckType {
        TRACTEUR(true, 32, 120), PORTEUR(true, 25, 90), FOURGON(true, 18, 60), VUL(false, 9, 20), VL(false, 6, 16);

        private final boolean heavy;
        private final double defaultConsumptionL100Km;
        private final double defaultConsumptionKwh100Km;

        TruckType(boolean heavy, double defaultConsumptionL100Km, double defaultConsumptionKwh100Km) {
            this.heavy = heavy;
            this.defaultConsumptionL100Km = defaultConsumptionL100Km;
            this.defaultConsumptionKwh100Km = defaultConsumptionKwh100Km;
        }

        public boolean isHeavy() {
            return heavy;
        }

        public double getDefaultConsumptionL100Km() {
            return defaultConsumptionL100Km;
        }

        /**
         * Consommation type aux 100 km, dans l'unité d'énergie du véhicule : kWh pour un
         * électrique, litres sinon.
         */
        public double defaultConsumption(FuelType fuel) {
            return fuel == FuelType.ELECTRIC ? defaultConsumptionKwh100Km : defaultConsumptionL100Km;
        }
    }

    public enum FuelType { DIESEL, ELECTRIC, ESSENCE, HYBRIDE, GNV }

    /** Le véhicule est soumis au règlement 561/2006 (temps de conduite tachygraphe). */
    public boolean requiresTachograph() {
        if (tachographEquipped != null) {
            return tachographEquipped;
        }
        return truckType == null || truckType.isHeavy();
    }

    /** Consommation de référence : valeur saisie, sinon valeur type de la catégorie. */
    public double referenceConsumption() {
        if (expectedConsumptionL100Km != null && expectedConsumptionL100Km > 0) {
            return expectedConsumptionL100Km;
        }
        return truckType != null ? truckType.defaultConsumption(fuelType) : 32;
    }
}
