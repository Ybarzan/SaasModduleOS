package com.incokalk.service.ecommerce;

import com.incokalk.model.ECommerceIntegration;
import com.incokalk.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class WooCommerceAdapter implements ECommerceAdapter {

    private final RestTemplate restTemplate;

    @Override
    public boolean supports(ECommerceIntegration.Platform platform) {
        return platform == ECommerceIntegration.Platform.WOOCOMMERCE;
    }

    @Override
    public List<Map<String, Object>> syncOrders(ECommerceIntegration integration) {
        List<Map<String, Object>> allOrders = new ArrayList<>();
        String baseUrl = integration.getStoreUrl().replaceAll("/$", "") + "/wp-json/wc/v3";
        int page = 1;

        try {
            while (page < 100) {
                StringBuilder urlBuilder = new StringBuilder(baseUrl)
                    .append("/orders?per_page=100&page=").append(page);
                if (integration.getLastSyncAt() != null) {
                    urlBuilder.append("&after=").append(integration.getLastSyncAt()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")));
                }
                String url = urlBuilder.toString();

                HttpHeaders headers = createAuthHeaders(integration);
                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<Map[]> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, Map[].class);

                Map[] body = response.getBody();
                if (body == null || body.length == 0) break;

                for (Map<String, Object> order : body) {
                    allOrders.add(order);
                }

                if (body.length < 100) break;
                page++;
            }

            log.info("[WooCommerce] {} orders fetched for integration {}", allOrders.size(), integration.getId());
        } catch (Exception e) {
            log.error("[WooCommerce] Sync failed for integration {}: {}", integration.getId(), e.getMessage());
        }

        return allOrders;
    }

    @Override
    public Map<String, Object> getOrder(ECommerceIntegration integration, String orderId) {
        String baseUrl = integration.getStoreUrl().replaceAll("/$", "") + "/wp-json/wc/v3";
        String url = baseUrl + "/orders/" + orderId;

        HttpHeaders headers = createAuthHeaders(integration);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            if (response.getBody() != null) {
                return response.getBody();
            }
        } catch (Exception e) {
            log.error("[WooCommerce] Failed to fetch order {}: {}", orderId, e.getMessage());
        }
        return Collections.emptyMap();
    }

    // mapOrderToShipment a ete retiree avec le domaine Transport
    // (ShipmentOrderRepository). Voir ECommerceAdapter / ECommerceSyncService.

    private HttpHeaders createAuthHeaders(ECommerceIntegration integration) {
        String auth = integration.getApiKey() + ":" + integration.getApiSecret();
        String encoded = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + encoded);
        return headers;
    }

    // markOrderShipped/pushEta (Phase 1 J4) non implémentées pour WooCommerce
    // -- voir ECommerceAdapter.markOrderShipped pour le pourquoi (vérifié
    // uniquement pour Shopify dans cette passe). Échec explicite plutôt
    // qu'un faux succès.
    @Override
    public PushResult markOrderShipped(ECommerceIntegration integration, String externalOrderId,
                                        String trackingNumber, String carrierName, boolean notifyCustomer) {
        return PushResult.fail("markOrderShipped non implémenté pour WooCommerce");
    }

    @Override
    public PushResult pushEta(ECommerceIntegration integration, String externalOrderId, java.time.LocalDateTime estimatedDeliveryDate) {
        return PushResult.fail("pushEta non implémenté pour WooCommerce");
    }
}
