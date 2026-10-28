package com.incokalk.service.erp;

/**
 * Résultat d'un export réel vers l'ERP -- {@code externalReference} identifie
 * l'enregistrement ERP réellement modifié sur un succès (jamais renseigné sur
 * un échec). Voir {@link ErpProvider#exportAdjustmentNote}.
 */
public record ErpExportResult(boolean success, String externalReference, String message) {

    public static ErpExportResult ok(String externalReference, String message) {
        return new ErpExportResult(true, externalReference, message);
    }

    public static ErpExportResult fail(String message) {
        return new ErpExportResult(false, null, message);
    }
}
