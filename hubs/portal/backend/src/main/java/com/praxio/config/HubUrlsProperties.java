package com.praxio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** URL de base de chaque hub -- fixées au déploiement, jamais éditables via l'API (contrairement aux clés). */
@ConfigurationProperties(prefix = "praxio.hubs")
public record HubUrlsProperties(
        String complianceEngineUrl,
        String fleetHubUrl,
        String logistixUrl
) {}
