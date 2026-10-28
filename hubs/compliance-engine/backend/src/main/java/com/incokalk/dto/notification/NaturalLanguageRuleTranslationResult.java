package com.incokalk.dto.notification;

import com.incokalk.model.NotificationRule;

/**
 * Résultat d'une tentative de traduction langage naturel -> règle. {@code rule}
 * n'est jamais {@code null} sur un succès ET toujours créée avec
 * {@code isActive=false} -- jamais prétendre qu'une règle générée par un
 * modèle est prête à tourner sans qu'un humain ne l'ait relue et activée
 * (docs/03-plan-migration.md Phase 3 J9-J10 : "validation humaine obligatoire
 * avant activation"). Sur un échec, {@code rule} est {@code null} et
 * {@code errorMessage} explique pourquoi -- jamais de règle partielle ou
 * deviné silencieusement en place d'un échec.
 */
public record NaturalLanguageRuleTranslationResult(boolean success, NotificationRule rule, String errorMessage) {

    public static NaturalLanguageRuleTranslationResult ok(NotificationRule rule) {
        return new NaturalLanguageRuleTranslationResult(true, rule, null);
    }

    public static NaturalLanguageRuleTranslationResult fail(String errorMessage) {
        return new NaturalLanguageRuleTranslationResult(false, null, errorMessage);
    }
}
