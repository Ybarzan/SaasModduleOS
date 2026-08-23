package com.fleethub.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Règle d'alerte configurable par société. Les règles actives contrôlent la
 * génération des notifications (type + seuil). Des règles par défaut sont
 * créées automatiquement au premier balayage.
 */
@Entity
@Table(name = "notification_rule")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertType type;

    /** Seuil déclencheur propre au type (jours d'avance, heures, nb d'événements…). */
    private Double threshold;

    @Column(nullable = false)
    private boolean enabled = true;

    public enum AlertType {
        /** Entretien dont l'échéance approche (seuil = jours avant échéance). */
        MAINTENANCE_ECHEANCE,
        /** Jour non conforme tachygraphe détecté. */
        TACHYGRAPHIE_NON_CONFORME,
        /** Temps de conduite hebdomadaire dépassé (seuil = heures). */
        TEMPS_CONDUITE,
        /** Usage anormal / alerte véhicule (vitesse, freinage…). */
        USAGE_ANORMAL,
        /** Paiement en échec (facturation). */
        PAIEMENT
    }
}
