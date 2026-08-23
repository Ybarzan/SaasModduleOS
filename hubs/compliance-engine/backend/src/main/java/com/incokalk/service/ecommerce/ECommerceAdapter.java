package com.incokalk.service.ecommerce;

import com.incokalk.model.ECommerceIntegration;
import com.incokalk.model.ShipmentOrder;

import java.util.List;
import java.util.Map;

public interface ECommerceAdapter {

    boolean supports(ECommerceIntegration.Platform platform);

    List<Map<String, Object>> syncOrders(ECommerceIntegration integration);

    Map<String, Object> getOrder(ECommerceIntegration integration, String orderId);

    ShipmentOrder mapOrderToShipment(Map<String, Object> order, ECommerceIntegration integration);
}
