package com.fleethub.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Événement de pointage déclaré par le chauffeur lui-même (portail dédié,
 * rôle CHAUFFEUR) : début / pause / reprise / fin de service. Complète la
 * tachygraphie (a posteriori) par une déclaration en temps réel, avec alertes
 * de pause immédiates côté chauffeur.
 */
@Entity
@Table(name = "pointage_event")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PointageEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public enum Type {
        DEBUT, PAUSE_DEBUT, PAUSE_FIN, FIN
    }
}
