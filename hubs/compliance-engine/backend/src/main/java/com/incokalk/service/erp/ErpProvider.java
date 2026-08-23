package com.incokalk.service.erp;

import com.incokalk.model.ErpConfig;
import com.incokalk.model.ShipmentOrder;

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

    ErpSyncResult exportShipments(ErpConfig config, List<ShipmentOrder> shipments);

    ErpSyncResult exportOrders(ErpConfig config, List<ShipmentOrder> orders);

    List<Map<String, Object>> getProducts(ErpConfig config);

    List<Map<String, Object>> getOrders(ErpConfig config);

    List<Map<String, Object>> getContacts(ErpConfig config);

    default boolean isAvailable(UUID companyId) {
        return true;
    }
}
