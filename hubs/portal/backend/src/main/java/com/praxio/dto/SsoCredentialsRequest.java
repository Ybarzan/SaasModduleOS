package com.praxio.dto;

/**
 * Mise à jour partielle des identifiants SSO par hub : un champ {@code null}
 * laisse la valeur existante inchangée, une chaîne vide l'efface, une valeur
 * non vide la remplace -- même convention que DashboardKeysRequest.
 */
public record SsoCredentialsRequest(
        String complianceEngineEmail,
        String complianceEnginePassword,
        String fleetHubEmail,
        String fleetHubPassword,
        String logistixEmail,
        String logistixPassword,
        String financeEmail,
        String financePassword
) {}
