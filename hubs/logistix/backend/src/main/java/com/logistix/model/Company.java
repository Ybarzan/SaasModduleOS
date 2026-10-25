package com.logistix.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Societe cliente (tenant) de Logistix. Volontairement minimal : pas de plan,
 * pas de facturation — Logistix n'a pas (encore) de systeme de billing.
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

    /** Clé d'intégration fleet-hub (en-tête X-Internal-Integration-Key) --
     *  générée côté fleet-hub quand cette société y active le partage
     *  d'expéditions, puis collée ici par un admin. Voir
     *  service/integration/fleethub/FleetHubIntegrationClient. Chiffrée au
     *  repos (voir EncryptedApiKeyConverter) : jamais recherchée par égalité
     *  ici, seulement relue en clair pour l'en-tête sortant. */
    @Column(name = "fleethub_api_key", length = 255)
    @Convert(converter = com.logistix.security.EncryptedApiKeyConverter.class)
    private String fleetHubApiKey;

    // ---- Intégration entrante Praxio (opt-in) ----
    /** Autorise la lecture d'un résumé d'activité (GET /v1/internal/dashboard-summary)
     *  par le tableau de bord cross-hub de Praxio pour cette société, via une
     *  clé qui lui est propre. Désactivé par défaut. */
    @Column(name = "praxio_integration_enabled", nullable = false)
    @Builder.Default
    private boolean praxioIntegrationEnabled = false;

    /** Hash SHA-256 (voir ApiKeyHasher) de la clé d'authentification
     *  machine-à-machine pour cette intégration (présentée via l'en-tête
     *  X-Internal-Integration-Key), générée à l'activation et affichée une
     *  seule fois -- ce champ ne contient jamais la clé en clair, seulement
     *  son hash, comparé à PraxioIntegrationService.resolveCompanyId(). */
    @Column(name = "praxio_integration_api_key", length = 64)
    private String praxioIntegrationApiKey;

    private static final SecureRandom API_KEY_RANDOM = new SecureRandom();

    /** Génère une clé API machine-à-machine pour l'intégration entrante
     *  Praxio (32 caractères hexadécimaux), sur le même format que la clé
     *  d'intégration interne de fleet-hub. */
    public static String generatePraxioIntegrationApiKey() {
        byte[] bytes = new byte[16];
        API_KEY_RANDOM.nextBytes(bytes);
        StringBuilder sb = new StringBuilder(32);
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
