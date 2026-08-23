package com.fleethub.dto;

/**
 * Résultat d'un test de connexion à un fournisseur externe.
 * {@code ok=false} signifie que la connexion a échoué (réseau, 4xx/5xx…) ;
 * {@code statusCode} est le code HTTP obtenu s'il y en a un.
 */
public record IntegrationTestResultDto(
        boolean ok,
        String message,
        Integer statusCode,
        long latencyMs) {
}
