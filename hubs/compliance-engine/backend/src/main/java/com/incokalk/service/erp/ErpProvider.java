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
    // domaine Transport. ErpSyncService.executeSync() traite desormais les
    // directions EXPORT des types ORDERS/SHIPMENTS/INVOICES comme un no-op en
    // attendant une reconstruction via fleet-hub (voir ErpSyncService).

    List<Map<String, Object>> getProducts(ErpConfig config);

    List<Map<String, Object>> getOrders(ErpConfig config);

    List<Map<String, Object>> getContacts(ErpConfig config);

    default boolean isAvailable(UUID companyId) {
        return true;
    }
}
