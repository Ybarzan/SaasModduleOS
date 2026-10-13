package com.praxio.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * URL de base (backend) et URL du frontend de chaque hub -- fixées au
 * déploiement, jamais éditables via l'API (contrairement aux clés/identifiants).
 * L'URL frontend sert de cible de redirection pour le relais SSO (voir SsoService) :
 * Praxio appelle le backend du hub mais renvoie le navigateur vers son frontend.
 */
@ConfigurationProperties(prefix = "praxio.hubs")
public record HubUrlsProperties(
        String complianceEngineUrl,
        String complianceEngineFrontendUrl,
        String fleetHubUrl,
        String fleetHubFrontendUrl,
        String logistixUrl,
        String logistixFrontendUrl
) {}
