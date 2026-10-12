package com.incokalk.service.ecommerce;

import com.incokalk.model.ECommerceIntegration;
import com.incokalk.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ShopifyAdapter implements ECommerceAdapter {

    private final RestTemplate restTemplate;
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

    // mapOrderToShipment a ete retiree avec le domaine Transport
    // (ShipmentOrderRepository). Voir ECommerceAdapter / ECommerceSyncService.
}
