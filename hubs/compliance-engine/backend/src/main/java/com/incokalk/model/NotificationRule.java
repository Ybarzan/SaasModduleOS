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

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
