package com.fleethub.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "tachograph_day")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TachographDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private double drivingHours;

    private double workHours;

    private double restMinutes;

    @Column(nullable = false)
    private boolean compliant;

    /** Provenance de la donnée (saisie manuelle, import CSV, import DDD non
     *  validé...) — voir {@link com.fleethub.integration.dto.TachographDayDto#source()}.
     *  Null pour une saisie manuelle ou les jours créés avant ce champ. */
    private String dataSource;
}
