package com.fleethub.dto;

/**
 * Statut du partage d'événements d'expédition vers compliance-engine. La clé
 * elle-même n'est jamais renvoyée une fois enregistrée -- même si elle est
 * chiffrée de façon réversible (voir EncryptedApiKeyConverter), la convention
 * de ce dépôt pour un secret saisi par un admin est de ne plus jamais
 * l'afficher ensuite (même principe que fleetHubApiKey, marketplaceApiKey…).
 */
public record ComplianceEngineWebhookSettingsDto(boolean configured) {
}
