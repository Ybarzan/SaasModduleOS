package com.fleethub.dto;

import com.fleethub.model.IntegrationProvider;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Configuration d'intégration vue par le client (ADMIN). La clé API n'est
 * jamais renvoyée : seuls {@code hasApiKey} (et son suffixe masqué) le sont.
 */
public record IntegrationConfigDto(
        Long id,
        IntegrationProvider provider,
        String providerLabel,
        String category,
        String baseUrl,
        boolean enabled,
        boolean hasApiKey,
        String apiKeyMasked,
        Map<String, String> settings,
        String webhookKey,
        LocalDateTime lastTestAt,
        Boolean lastTestOk,
        String lastTestMessage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
