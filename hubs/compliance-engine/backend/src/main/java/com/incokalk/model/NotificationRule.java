package com.incokalk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_rules")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class NotificationRule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    @JsonIgnore
    private Company company;

    @Column(nullable = false)
    private String name;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "send_email")
    @Builder.Default
    private Boolean sendEmail = false;

    @Column(name = "send_webhook")
    @Builder.Default
    private Boolean sendWebhook = false;

    @Column(name = "send_in_app")
    @Builder.Default
    private Boolean sendInApp = true;

    @Column(name = "email_recipients", columnDefinition = "TEXT")
    private String emailRecipients;

    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    // Jamais sérialisé dans les réponses API (GET/list/create/update) : le secret HMAC
    // ne doit être connu que du backend et de l'auteur qui l'a saisi côté client.
    // Voir ApiKey.keyHash pour la même convention sur un autre secret.
    @JsonIgnore
    @Column(name = "webhook_secret")
    private String webhookSecret;

    @Column(name = "filter_status")
    private String filterStatus;

    @Column(name = "filter_carrier_id")
    private UUID filterCarrierId;

    /** LIVE, MANUAL, ou null (pas de filtre) -- voir TrackingEvent.DataSource. */
    @Column(name = "filter_data_source", length = 10)
    private String filterDataSource;

    /** Arbre de condition composee serialise en JSON (RuleConditionNode), ou null.
     * Quand present, remplace filterStatus/filterCarrierId/filterDataSource pour
     * cette regle -- voir NotificationService.matchesFilters. */
    @Column(name = "condition_json", columnDefinition = "TEXT")
    private String conditionJson;

    /** null/"NONE" = notification seule (comportement historique). Une valeur
     * comme "SUGGEST_ERP_ORDER_ADJUSTMENT" fait naitre une OrchestrationSuggestion
     * EN PLUS de la notification -- jamais a la place, et jamais executee
     * automatiquement (voir V65, risque R1 de docs/05-estimation-couts-risques.md). */
    @Column(name = "action_type", length = 50)
    private String actionType;

    /** Destine a permettre a une regle de sauter la validation humaine
     * (requiresApproval=false). OrchestrationExecutor existe desormais mais ne lit
     * jamais ce champ -- toute suggestion reste PENDING_APPROVAL quelle que soit sa
     * valeur, contrairement a maxBudgetAmount/allowedCarrierIds ci-dessous qui sont,
     * eux, reellement verifies a l'execution. Champ toujours sans effet, mais plus
     * pour la meme raison ("executeur pas construit") -- c'est desormais un choix
     * deliberement non implemente (R1 : jamais de saut de validation humaine sans
     * decision produit explicite), pas un manque technique. */
    @Column(name = "requires_approval", nullable = false)
    @Builder.Default
    private boolean requiresApproval = true;

    /** Plafond de gouvernance (n'execute pas si le cout de l'expedition depasse ce
     * montant) -- reellement verifie par OrchestrationExecutor.checkGovernance()
     * avant tout appel externe (V66). */
    @Column(name = "max_budget_amount", precision = 15, scale = 2)
    private java.math.BigDecimal maxBudgetAmount;

    /** UUID de transporteurs autorises, separes par des virgules, ou null (tous
     * autorises) -- perimetre de gouvernance de l'action, reellement verifie par
     * OrchestrationExecutor.checkGovernance() avant tout appel externe (V66). */
    @Column(name = "allowed_carrier_ids", columnDefinition = "TEXT")
    private String allowedCarrierIds;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
