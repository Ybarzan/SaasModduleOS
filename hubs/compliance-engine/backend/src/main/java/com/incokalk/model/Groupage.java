package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "groupages", indexes = {
    @Index(name = "idx_groupages_company", columnList = "company_id"),
    @Index(name = "idx_groupages_status", columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Groupage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 30)
    private String reference;

    @Column(nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.PLANNED;

    @Column(name = "transport_mode", length = 10)
    private String transportMode;

    @Column(name = "carrier_name", length = 120)
    private String carrierName;

    @Column(length = 100)
    private String origin;

    @Column(length = 100)
    private String destination;

    @Column(name = "capacity_weight_kg", precision = 12, scale = 2)
    private BigDecimal capacityWeightKg;

    @Column(name = "capacity_volume_m3", precision = 12, scale = 2)
    private BigDecimal capacityVolumeM3;

    @Column(name = "booked_weight_kg", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal bookedWeightKg = BigDecimal.ZERO;

    @Column(name = "booked_volume_m3", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal bookedVolumeM3 = BigDecimal.ZERO;

    @Column(name = "planned_departure")
    private LocalDate plannedDeparture;

    @Column(name = "planned_arrival")
    private LocalDate plannedArrival;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Status {
        PLANNED, FORMING, BOOKED, DEPARTED, DELIVERED, CANCELLED
    }
}
