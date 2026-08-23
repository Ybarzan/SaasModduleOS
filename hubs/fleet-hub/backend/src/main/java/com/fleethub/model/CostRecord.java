package com.fleethub.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.YearMonth;

@Entity
@Table(name = "cost_record")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CostRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "truck_id")
    private Truck truck;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id")
    private Driver driver;

    @Column(nullable = false)
    private YearMonth billingMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CostCategory category;

    @Column(nullable = false)
    private double amount;

    public enum CostCategory {
        SALAIRE, CARBURANT, MAINTENANCE, ASSURANCE, AMORTISSEMENT, PEAGES, AUTRE
    }
}
