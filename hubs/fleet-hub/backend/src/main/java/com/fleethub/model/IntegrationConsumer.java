package com.fleethub.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Hubs de la plateforme autorisés à appeler {@code /api/internal/**}, chacun avec
 * sa propre clé et uniquement les droits dont il a besoin : une clé Logistix
 * fuitée ne permet ni de créer des expéditions ni de lire la facturation.
 */
public enum IntegrationConsumer {

    /** Clé unique partagée d'avant V24, conservée telle quelle pour ne rien casser
     *  au déploiement. Tous les droits ; ne peut plus être générée, seulement révoquée. */
    LEGACY("Clé historique (partagée)", EnumSet.allOf(Scope.class)),
    COMPLIANCE_ENGINE("Compliance Engine", EnumSet.of(Scope.SHIPMENTS_READ, Scope.SHIPMENTS_CREATE, Scope.DASHBOARD_READ)),
    LOGISTIX("Logistix", EnumSet.of(Scope.SHIPMENTS_READ)),
    FINANCE("Finance", EnumSet.of(Scope.SHIPMENTS_READ)),
    PORTAL("Praxio (portail)", EnumSet.of(Scope.DASHBOARD_READ, Scope.BILLING_READ));

    public enum Scope {
        /** GET /api/internal/shipments/** */
        SHIPMENTS_READ,
        /** POST /api/internal/shipments — en plus de l'opt-in société allowExternalShipmentCreation */
        SHIPMENTS_CREATE,
        /** GET /api/internal/dashboard-summary */
        DASHBOARD_READ,
        /** GET /api/internal/billing-summary */
        BILLING_READ
    }

    private final String label;
    private final Set<Scope> scopes;

    IntegrationConsumer(String label, Set<Scope> scopes) {
        this.label = label;
        this.scopes = scopes;
    }

    public String getLabel() {
        return label;
    }

    public Set<Scope> getScopes() {
        return scopes;
    }

    public boolean allows(Scope scope) {
        return scopes.contains(scope);
    }
}
