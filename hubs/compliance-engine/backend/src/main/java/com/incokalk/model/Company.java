package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "companies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "logo_url")
    private String logoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Plan plan = Plan.FREE;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    // Programme de parrainage -- généré paresseusement (voir CompanyService),
    // pas de backfill en masse à la migration.
    @Column(name = "referral_code", length = 12)
    private String referralCode;

    @Column(name = "referred_by_company_id")
    private UUID referredByCompanyId;

    @Column(name = "referral_reward_granted", nullable = false)
    @Builder.Default
    private boolean referralRewardGranted = false;

    @Column(columnDefinition = "varchar(2000)")
    @Builder.Default
    private String settings = "{}";

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    /** Clé d'intégration fleet-hub (en-tête X-Internal-Integration-Key) --
     *  générée côté fleet-hub quand cette société y active le partage
     *  d'expéditions, puis collée ici par un admin. Voir
     *  service/integration/fleethub/FleetHubIntegrationClient. Chiffrée au
     *  repos (voir EncryptedApiKeyConverter) : jamais recherchée par égalité
     *  ici, seulement relue en clair pour l'en-tête sortant. */
    @Column(name = "fleethub_api_key", length = 255)
    @Convert(converter = com.incokalk.security.EncryptedApiKeyConverter.class)
    private String fleetHubApiKey;

    /** Clé d'intégration Finance & Trésorerie (en-tête X-Internal-Integration-Key)
     *  -- générée côté hubs/finance quand cette société y active le partage
     *  d'agrégats, puis collée ici par un admin. Voir
     *  service/integration/finance/FinanceIntegrationClient. Chiffrée au
     *  repos, même raison que fleetHubApiKey ci-dessus. */
    @Column(name = "finance_api_key", length = 255)
    @Convert(converter = com.incokalk.security.EncryptedApiKeyConverter.class)
    private String financeApiKey;

    // ---- Webhook entrant fleet-hub (opt-in) ----
    /** Autorise la réception d'événements d'expédition (POST
     *  /v1/webhooks/fleethub/shipment-events) depuis fleet-hub pour cette
     *  société, via une clé qui lui est propre. Désactivé par défaut. */
    @Column(name = "fleethub_webhook_enabled", nullable = false)
    @Builder.Default
    private boolean fleethubWebhookEnabled = false;

    /** Hash SHA-256 (voir ApiKeyHasher) de la clé d'authentification
     *  machine-à-machine pour ce webhook (présentée via l'en-tête
     *  X-FleetHub-Webhook-Key), générée à l'activation et affichée une seule
     *  fois -- ce champ ne contient jamais la clé en clair, seulement son
     *  hash, comparé dans FleetHubWebhookService.resolveCompanyId(). */
    @Column(name = "fleethub_webhook_key_hash", length = 64)
    private String fleethubWebhookKeyHash;

    private static final java.security.SecureRandom WEBHOOK_KEY_RANDOM = new java.security.SecureRandom();

    /** Génère une clé API machine-à-machine pour le webhook entrant fleet-hub
     *  (32 caractères hexadécimaux), même format que les clés d'intégration
     *  entrantes des autres hubs (ex. praxioIntegrationApiKey de logistix). */
    public static String generateFleetHubWebhookKey() {
        byte[] bytes = new byte[16];
        WEBHOOK_KEY_RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Plan {
        FREE, STARTER, PRO, ENTERPRISE
    }
}
