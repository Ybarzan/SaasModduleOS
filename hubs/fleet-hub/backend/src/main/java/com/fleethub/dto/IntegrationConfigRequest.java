package com.fleethub.dto;

import com.fleethub.model.IntegrationProvider;

import java.util.Map;

/**
 * Corps de création / mise à jour d'une configuration d'intégration.
 * {@code apiKey} est optionnel : si absent lors d'une mise à jour, la clé
 * existante est conservée.
 */
public record IntegrationConfigRequest(
        IntegrationProvider provider,
        String baseUrl,
        String apiKey,
        Boolean enabled,
        Map<String, String> settings) {

    public boolean isEnabled() {
        return enabled == null || enabled;
    }
}
