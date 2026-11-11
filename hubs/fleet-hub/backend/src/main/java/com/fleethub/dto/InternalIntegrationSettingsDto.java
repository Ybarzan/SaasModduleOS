package com.fleethub.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Réglages du partage vers les autres hubs : interrupteur général + une ligne par
 * hub consommateur (clé configurée ou non). Ne contient jamais de clé en clair.
 */
public record InternalIntegrationSettingsDto(
        boolean internalIntegrationEnabled,
        List<Key> keys
) {
    public record Key(
            String consumer,
            String label,
            List<String> scopes,
            boolean configured,
            /** 4 derniers caractères, pour reconnaître la clé (null pour la clé historique). */
            String keyHint,
            LocalDateTime createdAt,
            LocalDateTime lastUsedAt
    ) {}
}
