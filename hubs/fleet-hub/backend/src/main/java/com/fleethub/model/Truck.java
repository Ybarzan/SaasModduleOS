package com.fleethub.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "truck")
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

    private Double purchasePrice;

    private Double expectedConsumptionL100Km;

    private Double currentLatitude;
    private Double currentLongitude;
    private Double currentSpeedKph;

    @Enumerated(EnumType.STRING)
    private VehicleStatus currentStatus;

    private java.time.LocalDateTime lastGpsUpdate;

    @Column(nullable = false)
    private boolean active = true;

    public enum VehicleStatus {
        ROULAGE, ARRET, REPOS, ALERTE, IMMOBILISE
    }

    public enum TruckType { TRACTEUR, PORTEUR, FOURGON }
    public enum FuelType { DIESEL, ELECTRIC }
}
