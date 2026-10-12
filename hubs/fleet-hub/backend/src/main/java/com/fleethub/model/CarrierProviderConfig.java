package com.fleethub.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Configuration (clé API, priorité, état de santé) d'un fournisseur de devis
 * transport (DHL, Shippo, …) pour une société. Équivalent local, propre au
 * domaine Transport, du {@code ProviderConfig} générique de compliance-engine
 * (partagé là-bas avec ERP/fintech) — volontairement non importé : voir
 * decisions de la fusion Transport → FleetHub. Long/IDENTITY, pas UUID : table
 * neuve de FleetHub, jamais référencée par les entités Transport portées.
 */
@Entity
@Table(name = "carrier_provider_config", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"company_id", "provider_type"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CarrierProviderConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "provider_type", nullable = false, length = 50)
    private String providerType;

    @Column(name = "api_key_encrypted", length = 500)
    private String apiKeyEncrypted;

    @Column(name = "api_secret", length = 500)
    private String apiSecret;

    @Builder.Default
    private int priority = 100;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = false;

    @Column(name = "health_status", length = 20)
    @Builder.Default
    private String healthStatus = "UNKNOWN";

    @Column(name = "consecutive_failures")
    @Builder.Default
    private int consecutiveFailures = 0;

    @Column(name = "last_health_check")
    private LocalDateTime lastHealthCheck;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
