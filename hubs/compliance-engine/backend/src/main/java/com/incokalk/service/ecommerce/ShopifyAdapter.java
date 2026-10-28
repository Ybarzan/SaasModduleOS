package com.incokalk.service.ecommerce;

import com.incokalk.model.ECommerceIntegration;
import com.incokalk.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * markOrderShipped()/pushEta() (Phase 1 J4) sont volontairement restreintes à
 * ce qui est vérifiable : l'API REST Admin Shopify est legacy depuis le
 * 1er octobre 2024 (gelée, plus de nouvelles fonctionnalités) et Shopify
 * impose GraphQL pour toute nouvelle app publique depuis le 1er avril 2025 --
 * mais reste documentée et fonctionnelle pour ce qui suit.
 * <ul>
 *   <li>markOrderShipped : création de fulfillment via le flux
 *       fulfillment_orders -> POST /fulfillments.json (le remplaçant
 *       documenté de l'ancien POST /orders/{id}/fulfillments.json, retiré).
 *       Vérifié sur shopify.dev.</li>
 *   <li>pushEta : écrit l'ETA en métadonnée (namespace/key/value/type), un
 *       mécanisme Shopify générique et stable, pas une mise à jour de
 *       shipment_status -- la documentation officielle indique elle-même
 *       que shipment_status ne suit pas automatiquement un simple ajout de
 *       tracking, et la forme exacte de son API de mise à jour n'a pas pu
 *       être confirmée depuis cet environnement (shopify.dev inaccessible
 *       au moment de l'écriture, sources communautaires contradictoires) --
 *       volontairement non implémenté plutôt que deviné.</li>
 * </ul>
 */
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

    @Override
    public PushResult markOrderShipped(ECommerceIntegration integration, String externalOrderId,
                                        String trackingNumber, String carrierName, boolean notifyCustomer) {
        String baseUrl = integration.getStoreUrl().replaceAll("/$", "") + "/admin/api/2024-01";
        try {
            HttpHeaders headers = authHeaders(integration);
            ResponseEntity<Map> foResponse = restTemplate.exchange(
                    baseUrl + "/orders/" + externalOrderId + "/fulfillment_orders.json",
                    HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            Map foBody = foResponse.getBody();
            List<Map<String, Object>> fulfillmentOrders = foBody != null
                    ? (List<Map<String, Object>>) foBody.get("fulfillment_orders") : null;
            if (fulfillmentOrders == null || fulfillmentOrders.isEmpty()) {
                return PushResult.fail("Aucun fulfillment_order ouvert pour la commande " + externalOrderId);
            }

            List<Map<String, Object>> lineItemsByFulfillmentOrder = new ArrayList<>();
            for (Map<String, Object> fo : fulfillmentOrders) {
                if (!"open".equals(fo.get("status")) && !"in_progress".equals(fo.get("status"))) {
                    continue;
                }
                lineItemsByFulfillmentOrder.add(Map.of("fulfillment_order_id", fo.get("id")));
            }
            if (lineItemsByFulfillmentOrder.isEmpty()) {
                return PushResult.fail("Aucun fulfillment_order fulfillable (statut open/in_progress) pour la commande " + externalOrderId);
            }

            Map<String, Object> trackingInfo = new LinkedHashMap<>();
            if (trackingNumber != null) trackingInfo.put("number", trackingNumber);
            if (carrierName != null) trackingInfo.put("company", carrierName);

            Map<String, Object> fulfillment = new LinkedHashMap<>();
            fulfillment.put("line_items_by_fulfillment_order", lineItemsByFulfillmentOrder);
            if (!trackingInfo.isEmpty()) fulfillment.put("tracking_info", trackingInfo);
            fulfillment.put("notify_customer", notifyCustomer);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of("fulfillment", fulfillment), headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/fulfillments.json", HttpMethod.POST, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[Shopify] Commande {} marquée expédiée (tracking={})", externalOrderId, trackingNumber);
                return PushResult.ok("Fulfillment créé pour la commande " + externalOrderId);
            }
            return PushResult.fail("Réponse Shopify inattendue : " + response.getStatusCode());
        } catch (HttpClientErrorException e) {
            log.warn("[Shopify] markOrderShipped échoué pour la commande {}: {}", externalOrderId, e.getMessage());
            return PushResult.fail("Erreur Shopify " + e.getStatusCode() + " : " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("[Shopify] markOrderShipped échoué pour la commande {}: {}", externalOrderId, e.getMessage());
            return PushResult.fail("Erreur inattendue : " + e.getMessage());
        }
    }

    @Override
    public PushResult pushEta(ECommerceIntegration integration, String externalOrderId, LocalDateTime estimatedDeliveryDate) {
        String baseUrl = integration.getStoreUrl().replaceAll("/$", "") + "/admin/api/2024-01";
        try {
            Map<String, Object> metafield = Map.of(
                    "namespace", "incokalk",
                    "key", "eta",
                    "value", estimatedDeliveryDate.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    "type", "single_line_text_field");

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(Map.of("metafield", metafield), authHeaders(integration));
            ResponseEntity<Map> response = restTemplate.exchange(
                    baseUrl + "/orders/" + externalOrderId + "/metafields.json",
                    HttpMethod.POST, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("[Shopify] ETA poussée en metafield pour la commande {}", externalOrderId);
                return PushResult.ok("Metafield ETA écrit pour la commande " + externalOrderId);
            }
            return PushResult.fail("Réponse Shopify inattendue : " + response.getStatusCode());
        } catch (HttpClientErrorException e) {
            log.warn("[Shopify] pushEta échoué pour la commande {}: {}", externalOrderId, e.getMessage());
            return PushResult.fail("Erreur Shopify " + e.getStatusCode() + " : " + e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("[Shopify] pushEta échoué pour la commande {}: {}", externalOrderId, e.getMessage());
            return PushResult.fail("Erreur inattendue : " + e.getMessage());
        }
    }

    private HttpHeaders authHeaders(ECommerceIntegration integration) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Shopify-Access-Token", integration.getApiSecret());
        return headers;
    }
}
