package com.fleethub.dto;

public record MarketplaceSettingsDto(
        boolean marketplaceOptIn,
        /** Non null uniquement juste après (ré)activation — montrée une fois, jamais renvoyée ensuite en clair. */
        String marketplaceApiKey
) {}
