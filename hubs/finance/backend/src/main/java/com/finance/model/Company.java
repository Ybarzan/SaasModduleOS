package com.finance.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Societe cliente (tenant) de Finance. Volontairement minimal : pas de plan,
 * pas de facturation propre — l'accès au module est déjà conditionné par le
 * plan ENTERPRISE côté compliance-engine, Finance ne revend rien en propre.
 */
@Entity
@Table(name = "companies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    // ---- Intégration entrante machine-à-machine (opt-in) ----
    /** Autorise la lecture des endpoints /v1/internal/** (résumé Praxio,
     *  agrégats pour FinanceIntegrationClient côté compliance-engine) pour
     *  cette société, via une seule clé partagée par tous les appelants
     *  machine-à-machine -- même principe que fleet-hub. Désactivé par défaut. */
    @Column(name = "internal_integration_enabled", nullable = false)
    @Builder.Default
    private boolean internalIntegrationEnabled = false;

    /** Clé d'authentification machine-à-machine (présentée via l'en-tête
     *  X-Internal-Integration-Key), générée à l'activation. Finance résout
     *  la société depuis cette clé -- jamais depuis un identifiant fourni
     *  par l'appelant. */
    @Column(name = "internal_integration_api_key", length = 64)
    private String internalIntegrationApiKey;

    // ---- Intégration SORTANTE vers fleet-hub (opt-in) ----
    /** Clé d'intégration fleet-hub (en-tête X-Internal-Integration-Key) --
     *  générée côté fleet-hub quand cette société y active le partage, puis
     *  collée ici par un admin (voir CompanySettingsController). Voir
     *  service/integration/fleethub/FleetHubIntegrationClient. Chiffrée au
     *  repos (voir EncryptedApiKeyConverter) : jamais recherchée par égalité
     *  ici, seulement relue en clair pour l'en-tête sortant -- contrairement
     *  à internalIntegrationApiKey ci-dessus (clé ENTRANTE, hashée). */
    @Column(name = "fleethub_api_key", length = 255)
    @Convert(converter = com.finance.security.EncryptedApiKeyConverter.class)
    private String fleetHubApiKey;

    private static final SecureRandom API_KEY_RANDOM = new SecureRandom();

    /** Génère une clé API machine-à-machine (32 caractères hexadécimaux). */
    public static String generateApiKey() {
        byte[] bytes = new byte[16];
        API_KEY_RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
