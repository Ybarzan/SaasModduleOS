package com.logistix.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

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
     *  service/integration/fleethub/FleetHubIntegrationClient. */
    @Column(name = "fleethub_api_key", length = 64)
    private String fleetHubApiKey;
}
