package com.finance.security;

import java.util.UUID;

/**
 * Contexte du tenant courant pour la requête en cours (ThreadLocal).
 * Renseigné par {@link TenantFilter} à partir du principal authentifié,
 * nettoyé en fin de requête. Utilisé par les services métier pour scoper
 * toutes les requêtes par société et rattacher les nouvelles entités à la
 * bonne entreprise.
 */
public final class TenantContext {

    private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(UUID companyId) {
        CURRENT.set(companyId);
    }

    public static UUID get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
