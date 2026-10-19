package com.finance.model;

/**
 * Role applicatif minimal pour Finance (pas de systeme de plan/billing propre --
 * l'accès est déjà conditionné côté compliance-engine par le plan ENTERPRISE).
 * ADMIN : accès complet, y compris facturation client et affacturage.
 * MANAGER : paiements, dashboard carbone.
 * USER : lecture.
 */
public enum Role {
    ADMIN, MANAGER, USER
}
