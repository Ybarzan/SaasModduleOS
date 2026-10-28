package com.incokalk.service.erp;

/**
 * Note d'ajustement à reporter sur une commande fournisseur existante dans
 * l'ERP -- voir {@link ErpProvider#exportAdjustmentNote}. {@code reference}
 * est la référence lisible par un humain de l'expédition (ex. "SHP-2026-001"),
 * utilisée pour RETROUVER une commande déjà existante dans l'ERP (jamais pour
 * en créer une) -- voir le javadoc de l'interface pour pourquoi.
 */
public record ErpAdjustmentNote(String reference, String body) {
}
