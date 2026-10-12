package com.praxio.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Configuration singleton (une seule ligne, id toujours {@link #SINGLETON_ID})
 * des clés d'intégration cross-hub de Praxio. Praxio n'est pas multi-tenant :
 * une seule instance sert un seul tableau de bord.
 */
@Entity
@Table(name = "dashboard_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardConfig {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    /** Clé API générale compliance-engine (préfixe ic_live_), via X-API-Key. */
    private String complianceEngineApiKey;

    /** Clé d'intégration interne fleet-hub, via X-Internal-Integration-Key. */
    private String fleetHubApiKey;

    /** Clé d'intégration Praxio Logistix, via X-Internal-Integration-Key. */
    private String logistixApiKey;
}
