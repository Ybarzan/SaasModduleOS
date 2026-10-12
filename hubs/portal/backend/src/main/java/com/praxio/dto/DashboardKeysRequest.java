package com.praxio.dto;

/**
 * Mise à jour partielle des 3 clés : un champ {@code null} laisse la clé
 * existante inchangée, une chaîne vide efface la clé, une valeur non vide la
 * remplace. Jamais de validation @NotBlank ici -- contrairement aux clés
 * "sortantes" des autres hubs, effacer une clé est une opération normale.
 */
public record DashboardKeysRequest(
        String complianceEngineApiKey,
        String fleetHubApiKey,
        String logistixApiKey
) {}
