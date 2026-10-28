package com.incokalk.service.erp;

import com.incokalk.model.ErpConfig;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface ErpProvider {

    String getErpType();

    String getName();

    boolean testConnection(ErpConfig config);

    ErpSyncResult importProducts(ErpConfig config);

    ErpSyncResult importOrders(ErpConfig config);

    ErpSyncResult importContacts(ErpConfig config);

    // exportShipments/exportOrders (List<ShipmentOrder>) ont ete retirees avec le
    // domaine Transport. ErpSyncService.executeSync() traite toujours les
    // directions EXPORT bulk des types ORDERS/SHIPMENTS/INVOICES comme un no-op
    // (creer une commande fournisseur de zero demanderait de resoudre un
    // partenaire/produit ERP qu'on n'a aucun moyen fiable d'identifier depuis une
    // expedition fleet-hub -- voir ErpSyncService). exportAdjustmentNote()
    // ci-dessous couvre un besoin plus etroit mais verifiable : OrchestrationExecutor.

    /**
     * Reporte un ajustement sur une commande fournisseur EXISTANTE dans l'ERP,
     * retrouvee par sa référence (jamais créée de zéro -- voir
     * {@link ErpAdjustmentNote}). Utilisé par {@code OrchestrationExecutor}
     * (docs/03-plan-migration.md Phase 3 J6-J8) : aucune commande correspondante
     * trouvée n'est jamais une erreur de ce connecteur, juste un
     * {@link ErpExportResult#fail} explicite -- l'appelant décide alors du repli
     * (par ex. NEEDS_MANUAL_EXPORT). Ne lève jamais d'exception.
     */
    ErpExportResult exportAdjustmentNote(ErpConfig config, ErpAdjustmentNote note);

    List<Map<String, Object>> getProducts(ErpConfig config);

    List<Map<String, Object>> getOrders(ErpConfig config);

    List<Map<String, Object>> getContacts(ErpConfig config);

    default boolean isAvailable(UUID companyId) {
        return true;
    }
}
