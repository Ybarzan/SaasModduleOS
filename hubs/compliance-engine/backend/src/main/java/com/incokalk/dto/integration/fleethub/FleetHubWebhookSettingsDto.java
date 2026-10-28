package com.incokalk.dto.integration.fleethub;

public record FleetHubWebhookSettingsDto(
        boolean fleethubWebhookEnabled,
        /** Non null uniquement juste après (ré)activation — montrée une fois, jamais renvoyée ensuite en clair. */
        String fleethubWebhookKey
) {}
