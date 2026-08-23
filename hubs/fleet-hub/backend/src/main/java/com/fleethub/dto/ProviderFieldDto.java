package com.fleethub.dto;

/**
 * Champ configurable d'un fournisseur (ex. chemin de test, identifiant compte).
 */
public record ProviderFieldDto(
        String name,
        String label,
        String placeholder,
        boolean required) {
}
