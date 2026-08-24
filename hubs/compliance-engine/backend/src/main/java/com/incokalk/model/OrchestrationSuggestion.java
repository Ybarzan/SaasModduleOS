package com.incokalk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Une action candidate proposee par une regle a actionType non-null --
 * jamais une action deja executee. Voir V65 et docs/04-composants-techniques.md.
 * Le passage a EXECUTED (appel reel a un systeme aval) est un chantier separe,
 * pas encore construit -- cette table s'arrete a la proposition + decision
 * humaine (APPROVED/REJECTED).
 */
@Entity
@Table(name = "orchestration_suggestions")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class OrchestrationSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rule_id", nullable = false)
    @JsonIgnore
    private NotificationRule rule;

    @Column(name = "shipment_id")
    private UUID shipmentId;

    @Column(name = "action_type", nullable = false, length = 50)
    private String actionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING_APPROVAL;

    /** Instantane des donnees de l'evenement qui a declenche la proposition
     * (le templateData de SendNotificationDTO) -- pour que la decision humaine
     * s'appuie sur ce qui a ete vu au moment de la proposition, pas sur l'etat
     * courant qui a pu changer depuis. */
    @Column(name = "context_json", columnDefinition = "TEXT")
    private String contextJson;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decided_by_user_id")
    private UUID decidedByUserId;

    @Column(name = "decision_note", length = 1000)
    private String decisionNote;

    public enum Status {
        PENDING_APPROVAL,
        APPROVED,
        REJECTED,
        EXECUTED,
        FAILED
    }
}
