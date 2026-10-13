package com.finance.dto;

public record InternalIntegrationSettingsDto(
        boolean internalIntegrationEnabled,
        /** Non null uniquement juste après (ré)activation — montrée une fois, jamais renvoyée ensuite en clair. */
        String internalIntegrationApiKey
) {}
