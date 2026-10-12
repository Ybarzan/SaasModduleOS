package com.incokalk.service.ecommerce;

import com.incokalk.model.ECommerceIntegration;

import java.util.List;
import java.util.Map;

public interface ECommerceAdapter {

    boolean supports(ECommerceIntegration.Platform platform);

    List<Map<String, Object>> syncOrders(ECommerceIntegration integration);

    Map<String, Object> getOrder(ECommerceIntegration integration, String orderId);

    // mapOrderToShipment(...) -> ShipmentOrder a ete retiree avec le domaine
    // Transport. ECommerceSyncService.syncSingleIntegration() traite desormais la
    // synchronisation commande -> expedition comme un no-op en attendant une
    // reconstruction via fleet-hub (voir ECommerceSyncService).
}
