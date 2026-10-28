package com.fleethub.dto;

import jakarta.validation.constraints.NotBlank;

public record ComplianceEngineWebhookUpdateRequest(
        @NotBlank(message = "La clé webhook est requise") String webhookKey
) {}
