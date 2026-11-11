package com.fleethub.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Non-conformité d'un transport médical : écart entre produits collectés et
 * remis, rupture de la chaîne du froid, délai d'acheminement dépassé…
 * Détectée automatiquement à la clôture des passages ou déclarée par
 * l'exploitant ; sa clôture exige une action corrective.
 */
@Entity
@Table(name = "non_conformity")
@Getter
@Setter
@NoArgsConstructor
public class NonConformity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "tour_id")
    private Long tourId;

    @Column(name = "tour_stop_id")
    private Long tourStopId;

    private String tourName;
    private LocalDate tourDate;
    private String siteName;

    @Enumerated(EnumType.STRING)
    @Column(name = "nc_type", nullable = false, length = 32)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Severity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.OUVERTE;

    /** true = détectée par le système, false = déclarée par un utilisateur. */
    @Column(nullable = false)
    private boolean automatic;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false)
    private LocalDateTime detectedAt;

    @Column(nullable = false)
    private String detectedBy;

    @Column(length = 1000)
    private String correctiveAction;

    private LocalDateTime closedAt;
    private String closedBy;

    public enum Type { ECART_QUANTITE, ECART_TEMPERATURE, DELAI_ACHEMINEMENT, COLLECTE_NON_REALISEE, EMBALLAGE, AUTRE }

    public enum Severity { MINEURE, MAJEURE, CRITIQUE }

    public enum Status { OUVERTE, EN_TRAITEMENT, CLOTUREE }
}
