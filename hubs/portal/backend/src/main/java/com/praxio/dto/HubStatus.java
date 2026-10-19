package com.praxio.dto;

/**
 * Enveloppe de statut par hub : {@code data} n'est renseigné que lorsque
 * {@code status == OK}. Permet au tableau de bord de continuer à afficher
 * les hubs disponibles même quand un autre est mal configuré ou inaccessible
 * (dégradation gracieuse, jamais une erreur globale 500).
 */
public record HubStatus<T>(Status status, T data) {

    public enum Status {
        OK,
        NOT_CONFIGURED,
        UNREACHABLE,
        NOT_APPLICABLE
    }

    public static <T> HubStatus<T> ok(T data) {
        return new HubStatus<>(Status.OK, data);
    }

    public static <T> HubStatus<T> notConfigured() {
        return new HubStatus<>(Status.NOT_CONFIGURED, null);
    }

    public static <T> HubStatus<T> unreachable() {
        return new HubStatus<>(Status.UNREACHABLE, null);
    }

    /** Le hub n'a pas (encore) cette fonctionnalité -- distinct de NOT_CONFIGURED : aucune clé ne la débloquerait. */
    public static <T> HubStatus<T> notApplicable() {
        return new HubStatus<>(Status.NOT_APPLICABLE, null);
    }
}
