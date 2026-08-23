package com.fleethub.security;

import com.fleethub.model.Company;

/**
 * Contexte du tenant courant pour la requête en cours (ThreadLocal).
 * Renseigné par {@link TenantFilter} à partir du JWT, nettoyé en fin de requête.
 * Utilisé pour scorer toutes les requêtes données par société et pour
 * rattacher les nouvelles entités à la bonne entreprise.
 */
public final class TenantContext {

    private static final ThreadLocal<Company> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void set(Company company) {
        CURRENT.set(company);
    }

    public static Company get() {
        return CURRENT.get();
    }

    public static Long companyId() {
        Company c = CURRENT.get();
        return c != null ? c.getId() : null;
    }

    public static Company require() {
        Company c = CURRENT.get();
        if (c == null) {
            throw new IllegalStateException("Aucun tenant actif pour cette requête");
        }
        return c;
    }

    public static boolean hasCompany() {
        return CURRENT.get() != null;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
