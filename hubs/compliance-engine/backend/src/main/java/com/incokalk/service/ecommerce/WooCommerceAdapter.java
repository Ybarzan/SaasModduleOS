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

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class WooCommerceAdapter implements ECommerceAdapter {

    private final RestTemplate restTemplate;
    private final ShipmentOrderRepository shipmentOrderRepository;

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
    public ShipmentOrder mapOrderToShipment(Map<String, Object> order, ECommerceIntegration integration) {
        Company company = integration.getCompany();

        Integer orderNumber = (Integer) order.get("number");
        String orderRef = "WC-" + (orderNumber != null ? orderNumber : order.get("id"));
        if (shipmentOrderRepository.findByOrderNumber(orderRef).isPresent()) {
            return null;
        }

        List<Map<String, Object>> lineItems = (List<Map<String, Object>>) order.getOrDefault("line_items", List.of());
        String goodsDesc = lineItems.stream()
            .map(item -> (String) item.getOrDefault("name", ""))
            .filter(s -> !s.isEmpty())
            .collect(Collectors.joining(", "));

        Map<String, Object> shipping = (Map<String, Object>) order.get("shipping");
        String consigneeName = shipping != null ? (String) shipping.get("first_name") + " " +
            (String) shipping.getOrDefault("last_name", "") : null;
        String consigneeAddress = shipping != null ? (String) shipping.get("address_1") : null;
        String consigneeCity = shipping != null ? (String) shipping.get("city") : null;
        String consigneeCountry = shipping != null ? (String) shipping.get("country") : null;
        String consigneePostalCode = shipping != null ? (String) shipping.get("postcode") : null;

        double totalWeight = lineItems.stream()
            .mapToDouble(item -> {
                Number weight = (Number) item.get("weight");
                if (weight != null) {
                    String unit = (String) item.get("weight_unit");
                    return "kg".equalsIgnoreCase(unit) ? weight.doubleValue() : weight.doubleValue() / 1000.0;
                }
                return 0;
            }).sum();

        String currency = (String) order.getOrDefault("currency", "EUR");
        String totalStr = (String) order.getOrDefault("total", "0");

        ShipmentOrder shipment = ShipmentOrder.builder()
            .company(company)
            .orderNumber(orderRef)
            .status(ShipmentOrder.Status.DRAFT)
            .goodsDescription(goodsDesc)
            .weightKg(totalWeight > 0 ? totalWeight : null)
            .packagesCount(lineItems.size())
            .currency(currency)
            .goodsValue(parseDouble(totalStr))
            .consigneeName(consigneeName != null ? consigneeName.trim() : null)
            .consigneeAddress(consigneeAddress)
            .consigneeCity(consigneeCity)
            .consigneeCountry(consigneeCountry)
            .consigneePostalCode(consigneePostalCode)
            .build();

        return shipmentOrderRepository.save(shipment);
    }

    private HttpHeaders createAuthHeaders(ECommerceIntegration integration) {
        String auth = integration.getApiKey() + ":" + integration.getApiSecret();
        String encoded = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Basic " + encoded);
        return headers;
    }

    private Double parseDouble(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
