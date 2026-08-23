package com.fleethub.dto;

import com.fleethub.model.IntegrationProvider;

import java.util.List;

/**
 * Métadonnées d'un fournisseur exposées au frontend pour construire le
 * formulaire d'ajout (champs, placeholder, mode d'authentification).
 */
public record ProviderMetaDto(
        IntegrationProvider name,
        String label,
        String category,
        String defaultBaseUrl,
        boolean usesBearerToken,
        List<ProviderFieldDto> fields) {
}
