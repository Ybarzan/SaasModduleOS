package com.incokalk.service.ecommerce;

import com.incokalk.model.ECommerceIntegration;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.service.integration.fleethub.ExternalShipmentCreationRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    @Override
    public String extractExternalOrderId(Map<String, Object> rawOrder) {
        Object id = rawOrder.get("id");
        return id != null ? String.valueOf(id) : null;
    }

    /**
     * Champs {@code shipping} (first_name/last_name/company/address_1/city/country/postcode)
     * et {@code line_items} (name/quantity), {@code total}/{@code currency} -- noms de
     * champs corroborés par le wiki officiel de données de commande de l'organisation
     * GitHub woocommerce/woocommerce et par un exemple d'intégration tierce indépendant
     * (recherche web), faute d'accès direct à developer.woocommerce.com depuis cet
     * environnement au moment de l'écriture. Note : {@code address_1} avec underscore,
     * contrairement à {@code address1} chez Shopify -- vérifié dans les deux sources, pas
     * une coquille. Aucun champ de poids/volume WooCommerce corroboré -- weightKg/volumeM3
     * volontairement non renseignés plutôt que devinés (contrairement à Shopify
     * total_weight, confirmé par ailleurs).
     */
    @Override
    @SuppressWarnings("unchecked")
    public Optional<ExternalShipmentCreationRequest> mapOrderToShipment(
            ECommerceIntegration integration, Map<String, Object> rawOrder) {
        Map<String, Object> shipping = (Map<String, Object>) rawOrder.get("shipping");
        if (shipping == null) {
            log.debug("[WooCommerce] Commande {} sans objet shipping -- ignorée", rawOrder.get("id"));
            return Optional.empty();
        }

        String consigneeName = buildConsigneeName(shipping);
        if (consigneeName == null) {
            log.debug("[WooCommerce] Commande {} : shipping sans nom ni société -- pas de mapping fiable",
                    rawOrder.get("id"));
            return Optional.empty();
        }

        String shipperName = integration.getCompany() != null ? integration.getCompany().getName() : null;
        if (shipperName == null || shipperName.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(ExternalShipmentCreationRequest.builder()
                .shipperName(shipperName)
                .consigneeName(consigneeName)
                .consigneeAddress(asString(shipping.get("address_1")))
                .consigneeCity(asString(shipping.get("city")))
                .consigneeCountry(asString(shipping.get("country")))
                .consigneePostalCode(asString(shipping.get("postcode")))
                .goodsDescription(describeLineItems(rawOrder))
                .goodsValue(asDouble(rawOrder.get("total")))
                .currency(asString(rawOrder.get("currency")))
                .build());
    }

    private String buildConsigneeName(Map<String, Object> shipping) {
        String first = asString(shipping.get("first_name"));
        String last = asString(shipping.get("last_name"));
        String personName = Stream.of(first, last).filter(s -> s != null && !s.isBlank())
                .collect(Collectors.joining(" "));
        if (!personName.isBlank()) return personName;
        String company = asString(shipping.get("company"));
        return (company != null && !company.isBlank()) ? company : null;
    }

    @SuppressWarnings("unchecked")
    private String describeLineItems(Map<String, Object> rawOrder) {
        List<Map<String, Object>> items = (List<Map<String, Object>>) rawOrder.get("line_items");
        if (items == null || items.isEmpty()) return null;
        return items.stream()
                .map(item -> {
                    Object qty = item.get("quantity");
                    Object name = item.get("name");
                    return (qty != null ? qty + "x " : "") + (name != null ? name : "Article");
                })
                .collect(Collectors.joining(", "));
    }

    private String asString(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private Double asDouble(Object value) {
        if (value == null) return null;
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

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
