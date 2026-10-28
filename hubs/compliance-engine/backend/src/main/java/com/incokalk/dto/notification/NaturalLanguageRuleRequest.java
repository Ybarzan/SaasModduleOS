package com.incokalk.dto.notification;

import jakarta.validation.constraints.NotBlank;

/** Description en langage naturel d'une règle à traduire -- voir
 *  com.incokalk.service.rules.NlRuleTranslationService. */
public record NaturalLanguageRuleRequest(@NotBlank(message = "La description est obligatoire") String description) {
}
