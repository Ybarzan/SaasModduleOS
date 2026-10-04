package com.fleethub.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Commande à planifier : un passage à effectuer un jour donné sur un site
 * (livraison, enlèvement, collecte), pas encore affecté à une tournée.
 * La répartition automatique la transforme en arrêt de tournée.
 */
@Entity
@Table(name = "delivery_order")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "order_date", nullable = false)
    private LocalDate date;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    /** Référence du client (n° de commande, de bon…). */
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_type", nullable = false, length = 32)
    private TourStop.StopType type = TourStop.StopType.LIVRAISON;

    private LocalTime windowStart;
    private LocalTime windowEnd;

    @Column(nullable = false)
    private int serviceMinutes = 5;

    /** Quantité (colis, sachets…) : sert à respecter la capacité des véhicules. */
    @Column(nullable = false)
    private int quantity = 1;

    private Double temperatureMinCelsius;
    private Double temperatureMaxCelsius;

    @Column(length = 1000)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status = OrderStatus.A_PLANIFIER;

    /** Arrêt créé par la planification (null tant que la commande n'est pas planifiée). */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tour_stop_id")
    private TourStop tourStop;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public enum OrderStatus { A_PLANIFIER, PLANIFIEE }
}
