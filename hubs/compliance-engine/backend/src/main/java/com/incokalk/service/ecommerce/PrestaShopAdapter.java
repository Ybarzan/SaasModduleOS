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

import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

@Slf4j
@Component
@RequiredArgsConstructor
public class PrestaShopAdapter implements ECommerceAdapter {

    private final RestTemplate restTemplate;
    private final ShipmentOrderRepository shipmentOrderRepository;

    @Override
    public boolean supports(ECommerceIntegration.Platform platform) {
        return platform == ECommerceIntegration.Platform.PRESTASHOP;
    }

    @Override
    public List<Map<String, Object>> syncOrders(ECommerceIntegration integration) {
        List<Map<String, Object>> allOrders = new ArrayList<>();
        String baseUrl = integration.getStoreUrl().replaceAll("/$", "") + "/api";
        String apiKey = integration.getApiKey();
        int page = 0;

        try {
            while (page < 100) {
                int limit = 100;
                int offset = page * limit;
                StringBuilder urlBuilder = new StringBuilder(baseUrl)
                    .append("/orders?display=full&limit=").append(offset).append(',').append(limit);
                if (integration.getLastSyncAt() != null) {
                    String since = integration.getLastSyncAt()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    urlBuilder.append("&filter[date_upd]=>[").append(since).append(']');
                }
                String url = urlBuilder.toString();

                HttpHeaders headers = createAuthHeaders(apiKey);
                HttpEntity<Void> entity = new HttpEntity<>(headers);
                ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

                String responseBody = response.getBody();
                if (responseBody == null || responseBody.isBlank()) break;

                List<Map<String, Object>> orders = parseOrdersXml(responseBody);
                if (orders.isEmpty()) break;

                allOrders.addAll(orders);

                if (orders.size() < limit) break;
                page++;
            }

            log.info("[PrestaShop] {} orders fetched for integration {}", allOrders.size(), integration.getId());
        } catch (Exception e) {
            log.error("[PrestaShop] Sync failed for integration {}: {}", integration.getId(), e.getMessage());
        }

        return allOrders;
    }

    @Override
    public Map<String, Object> getOrder(ECommerceIntegration integration, String orderId) {
        String baseUrl = integration.getStoreUrl().replaceAll("/$", "") + "/api";
        String url = baseUrl + "/orders/" + orderId + "?display=full";

        HttpHeaders headers = createAuthHeaders(integration.getApiKey());
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (response.getBody() != null) {
                List<Map<String, Object>> orders = parseOrdersXml(response.getBody());
                if (!orders.isEmpty()) return orders.get(0);
            }
        } catch (Exception e) {
            log.error("[PrestaShop] Failed to fetch order {}: {}", orderId, e.getMessage());
        }
        return Collections.emptyMap();
    }

    @Override
    public ShipmentOrder mapOrderToShipment(Map<String, Object> order, ECommerceIntegration integration) {
        Company company = integration.getCompany();

        String orderRef = "PS-" + order.get("id");
        if (shipmentOrderRepository.findByOrderNumber(orderRef).isPresent()) {
            return null;
        }

        String goodsDesc = (String) order.getOrDefault("products", "");
        String consigneeName = (String) order.get("delivery_firstname");
        String consigneeLastName = (String) order.get("delivery_lastname");
        if (consigneeLastName != null && consigneeName != null) {
            consigneeName = consigneeName + " " + consigneeLastName;
        }

        String consigneeAddress = (String) order.get("delivery_address1");
        String consigneeCity = (String) order.get("delivery_city");
        String consigneeCountry = (String) order.get("delivery_country");
        String consigneePostalCode = (String) order.get("delivery_postcode");

        Number totalWeightVal = (Number) order.get("total_weight");
        double totalWeight = totalWeightVal != null ? totalWeightVal.doubleValue() : 0;

        String currency = (String) order.getOrDefault("currency", "EUR");
        Number totalPaid = (Number) order.get("total_paid");

        ShipmentOrder shipment = ShipmentOrder.builder()
            .company(company)
            .orderNumber(orderRef)
            .status(ShipmentOrder.Status.DRAFT)
            .goodsDescription(goodsDesc)
            .weightKg(totalWeight > 0 ? totalWeight : null)
            .currency(currency)
            .goodsValue(totalPaid != null ? totalPaid.doubleValue() : 0)
            .consigneeName(consigneeName)
            .consigneeAddress(consigneeAddress)
            .consigneeCity(consigneeCity)
            .consigneeCountry(consigneeCountry)
            .consigneePostalCode(consigneePostalCode)
            .build();

        return shipmentOrderRepository.save(shipment);
    }

    private HttpHeaders createAuthHeaders(String apiKey) {
        String encoded = Base64.getEncoder().encodeToString((apiKey + ":").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        headers.setAccept(List.of(MediaType.APPLICATION_XML));
        headers.set("Authorization", "Basic " + encoded);
        return headers;
    }

    private List<Map<String, Object>> parseOrdersXml(String xml) {
        List<Map<String, Object>> orders = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(xml)));
            NodeList orderNodes = doc.getElementsByTagName("order");

            for (int i = 0; i < orderNodes.getLength(); i++) {
                Element orderEl = (Element) orderNodes.item(i);
                Map<String, Object> order = new LinkedHashMap<>();
                order.put("id", getChildText(orderEl, "id"));
                order.put("reference", getChildText(orderEl, "reference"));
                order.put("total_paid", parseDoubleSafe(getChildText(orderEl, "total_paid")));
                order.put("total_weight", parseDoubleSafe(getChildText(orderEl, "total_weight")));
                order.put("currency", getChildText(orderEl, "currency"));

                Element deliveryAddr = getChildElement(orderEl, "delivery_address");
                if (deliveryAddr != null) {
                    order.put("delivery_firstname", getChildText(deliveryAddr, "firstname"));
                    order.put("delivery_lastname", getChildText(deliveryAddr, "lastname"));
                    order.put("delivery_address1", getChildText(deliveryAddr, "address1"));
                    order.put("delivery_city", getChildText(deliveryAddr, "city"));
                    order.put("delivery_country", getChildText(deliveryAddr, "country"));
                    order.put("delivery_postcode", getChildText(deliveryAddr, "postcode"));
                }

                List<String> productNames = new ArrayList<>();
                NodeList assocs = orderEl.getElementsByTagName("order_row");
                for (int j = 0; j < assocs.getLength(); j++) {
                    Element row = (Element) assocs.item(j);
                    String name = getChildText(row, "product_name");
                    if (!name.isEmpty()) productNames.add(name);
                }
                order.put("products", String.join(", ", productNames));

                orders.add(order);
            }
        } catch (Exception e) {
            log.error("[PrestaShop] XML parse error: {}", e.getMessage());
        }
        return orders;
    }

    private String getChildText(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() > 0) {
            String text = list.item(0).getTextContent();
            return text != null ? text.trim() : "";
        }
        return "";
    }

    private Element getChildElement(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() > 0 && list.item(0) instanceof Element) {
            return (Element) list.item(0);
        }
        return null;
    }

    private Double parseDoubleSafe(String value) {
        try {
            return Double.parseDouble(value);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
