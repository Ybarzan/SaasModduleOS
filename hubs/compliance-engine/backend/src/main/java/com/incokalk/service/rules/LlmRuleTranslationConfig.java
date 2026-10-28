package com.incokalk.service.rules;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Configuration du service de traduction langage naturel -> règle structurée
 * (Phase 3 J9-J10, docs/03-plan-migration.md). Même convention que
 * incokalk.stripe/incokalk.fintech.qonto (clé vide = fonctionnalité désactivée,
 * voir BillingService). Pas de valeur par défaut pour {@code model} :
 * contrairement à {@code base-url} (l'URL de l'API Anthropic ne change pas),
 * un nom de modèle se périme -- figer une valeur ici reproduirait exactement
 * le bug corrigé pour aquasecurity/trivy-action (tag retiré en amont sans que
 * ce dépôt ne s'en rende compte) ; c'est à qui configure cette clé de choisir
 * un modèle actuellement disponible.
 */
@Component
@Getter
public class LlmRuleTranslationConfig {

    @Value("${incokalk.rules.nl-translation.api-key:}")
    private String apiKey;

    @Value("${incokalk.rules.nl-translation.base-url:https://api.anthropic.com}")
    private String baseUrl;

    @Value("${incokalk.rules.nl-translation.model:}")
    private String model;

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank();
    }
}
