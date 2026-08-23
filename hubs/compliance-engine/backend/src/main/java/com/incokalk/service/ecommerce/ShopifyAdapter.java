package com.incokalk.service.ecommerce;

import com.incokalk.model.Company;
import com.incokalk.model.ECommerceIntegration;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopifyAdapter implements ECommerceAdapter {

    private final RestTemplate restTemplate;
    private final ShipmentOrderRepository shipmentOrderRepository;
    private final CompanyRepository companyRepository;

    @Override
    public boolean supports(ECommerceIntegration.Platform platform) {
        return platform == ECommerceIntegration.Platform.SHOPIFY;
    }

    @Override
    public List<Map<String, Object>> syncOrders(ECommerceIntegration integration) {
        List<Map<String, Object>> allOrders = new ArrayList<>();
        String baseUrl = integration.getStoreUrl().replaceAll("/$", "") + "/admin/api/2024-01";
        String sinceId = null;

        try {
            int page = 0;
            while (page < 50) {
                String url = baseUrl + "/orders.json?status=any&limit=250";
                if (sinceId != null) {
                    url += "&since_id=" + sinceId;
                }
                if (integration.getLastSyncAt() != null) {
                    url += "&updated_at_min=" + integration.getLastSyncAt()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
                }

                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-Shopify-Access-Token", integration.getApiSecret());

                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);

                Map responseBody = response.getBody();
                if (responseBody == null) break;

                List<Map<String, Object>> orders = (List<Map<String, Object>>) responseBody.get("orders");
                if (orders == null || orders.isEmpty()) break;

                allOrders.addAll(orders);
                sinceId = String.valueOf(orders.get(orders.size() - 1).get("id"));
                page++;
            }

            log.info("[Shopify] {} orders fetched for integration {}", allOrders.size(), integration.getId());
        } catch (Exception e) {
            log.error("[Shopify] Sync failed for integration {}: {}", integration.getId(), e.getMessage());
        }

        return allOrders;
    }

    @Override
    public Map<String, Object> getOrder(ECommerceIntegration integration, String orderId) {
        String baseUrl = integration.getStoreUrl().replaceAll("/$", "") + "/admin/api/2024-01";
        String url = baseUrl + "/orders/" + orderId + ".json";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Shopify-Access-Token", integration.getApiSecret());

        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map responseBody = response.getBody();
            if (responseBody != null) {
                return (Map<String, Object>) responseBody.get("order");
            }
        } catch (Exception e) {
            log.error("[Shopify] Failed to fetch order {}: {}", orderId, e.getMessage());
        }
        return Collections.emptyMap();
    }

    @Override
    public ShipmentOrder mapOrderToShipment(Map<String, Object> order, ECommerceIntegration integration) {
        Company company = integration.getCompany();

        String orderNumber = "SHOP-" + order.get("order_number");
        if (shipmentOrderRepository.findByOrderNumber(orderNumber).isPresent()) {
            return null;
        }

        List<Map<String, Object>> lineItems = (List<Map<String, Object>>) order.getOrDefault("line_items", List.of());
        String goodsDesc = lineItems.stream()
            .map(item -> (String) item.getOrDefault("title", ""))
            .filter(s -> !s.isEmpty())
            .collect(Collectors.joining(", "));

        Map<String, Object> shippingAddress = (Map<String, Object>) order.get("shipping_address");
        String consigneeName = shippingAddress != null ? (String) shippingAddress.get("name") : null;
        String consigneeAddress = shippingAddress != null ? (String) shippingAddress.get("address1") : null;
        String consigneeCity = shippingAddress != null ? (String) shippingAddress.get("city") : null;
        String consigneeCountry = shippingAddress != null ? (String) shippingAddress.get("country_code") : null;
        String consigneePostalCode = shippingAddress != null ? (String) shippingAddress.get("zip") : null;

        double totalWeight = lineItems.stream()
            .mapToDouble(item -> {
                Object weight = item.get("grams");
                if (weight instanceof Number) {
                    return ((Number) weight).doubleValue() / 1000.0;
                }
                return 0;
            }).sum();

        String currency = (String) order.getOrDefault("currency", "EUR");
        Number totalPrice = (Number) order.getOrDefault("total_price", 0);

        ShipmentOrder shipment = ShipmentOrder.builder()
            .company(company)
            .orderNumber(orderNumber)
            .status(ShipmentOrder.Status.DRAFT)
            .goodsDescription(goodsDesc)
            .weightKg(totalWeight > 0 ? totalWeight : null)
            .packagesCount(lineItems.size())
            .currency(currency)
            .goodsValue(totalPrice != null ? totalPrice.doubleValue() : 0)
            .consigneeName(consigneeName)
            .consigneeAddress(consigneeAddress)
            .consigneeCity(consigneeCity)
            .consigneeCountry(consigneeCountry)
            .consigneePostalCode(consigneePostalCode)
            .build();

        return shipmentOrderRepository.save(shipment);
    }
}
