package com.praxio.dto;

/** Jamais les identifiants eux-mêmes -- seulement si chaque hub est configuré, et l'email (pas le mot de passe). */
public record SsoCredentialsStatusDto(
        boolean complianceEngineConfigured,
        String complianceEngineEmail,
        boolean fleetHubConfigured,
        String fleetHubEmail,
        boolean logistixConfigured,
        String logistixEmail,
        boolean financeConfigured,
        String financeEmail
) {}
