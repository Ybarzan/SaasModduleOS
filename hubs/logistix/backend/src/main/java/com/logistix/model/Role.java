package com.logistix.model;

/**
 * Role applicatif minimal pour Logistix (pas de systeme de plan/billing).
 * ADMIN : acces complet, y compris suppression/desactivation et annulation.
 * MANAGER : peut creer/modifier la configuration (entrepots, articles, bons de reception).
 * USER : lecture + operations de scan/reception du quotidien.
 */
public enum Role {
    ADMIN, MANAGER, USER
}
