package com.fleethub.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Événement du journal de garde (transport médical) : qui a pris en charge ou
 * remis quels produits, où, quand, à quelle température et à qui.
 * <p>
 * Le journal est en ajout seul et chaîné : {@link #hash} est l'empreinte
 * SHA-256 du contenu de l'événement et de {@link #previousHash}. Une ligne
 * modifiée ou supprimée a posteriori rompt la chaîne (voir CustodyLedger#verify).
 * Les noms de tournée et de site sont recopiés pour que le journal reste
 * lisible même si la tournée ou le site sont modifiés ensuite.
 */
@Entity
@Table(name = "custody_event")
@Getter
@Setter
@NoArgsConstructor
public class CustodyEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    /** Numéro d'ordre dans le journal de la société (1, 2, 3…). */
    @Column(name = "sequence_no", nullable = false)
    private long sequenceNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 32)
    private Type type;

    @Column(name = "tour_id")
    private Long tourId;

    @Column(name = "tour_stop_id")
    private Long tourStopId;

    private String tourName;
    private String siteName;

    @Column(length = 32)
    private String siteKind;

    /** Heure de l'action sur le terrain (téléphone, bornée). */
    @Column(nullable = false)
    private LocalDateTime occurredAt;

    /** Heure d'enregistrement par le serveur. */
    @Column(nullable = false)
    private LocalDateTime recordedAt;

    @Column(nullable = false)
    private String actor;

    /** Quantités par catégorie, format « SANG=3,PCR=2 ». */
    private String products;
    private Integer totalQuantity;
    private Double temperatureCelsius;
    private String signedBy;
    private Double latitude;
    private Double longitude;

    @Column(length = 1000)
    private String details;

    @Column(nullable = false, length = 64)
    private String previousHash;

    @Column(nullable = false, length = 64)
    private String hash;

    public enum Type {
        TOURNEE_DEMARREE,
        ARRIVEE,
        /** Produits pris en charge au point de collecte. */
        PRISE_EN_CHARGE,
        /** Produits remis au laboratoire / destinataire. */
        REMISE,
        ECHEC,
        /** Preuve de passage modifiée après coup (motif obligatoire). */
        CORRECTION,
        TOURNEE_TERMINEE,
        TOURNEE_ANNULEE,
        NC_OUVERTE,
        NC_CLOTUREE
    }
}
