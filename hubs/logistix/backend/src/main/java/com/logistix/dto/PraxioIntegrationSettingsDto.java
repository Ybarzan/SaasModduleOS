package com.logistix.dto;

public record PraxioIntegrationSettingsDto(
        boolean praxioIntegrationEnabled,
        /** Non null uniquement juste après (ré)activation — montrée une fois, jamais renvoyée ensuite en clair. */
        String praxioIntegrationApiKey
) {}
