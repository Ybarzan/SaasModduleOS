package com.incokalk.service.erp;

import com.incokalk.model.ErpConfig;
import com.incokalk.model.ShipmentOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class SapProvider implements ErpProvider {

    private final RestTemplate restTemplate;

    @Override
    public String getErpType() {
        return "SAP";
    }

    @Override
    public String getName() {
        return "SAP Business One";
    }

    @Override
    public boolean testConnection(ErpConfig config) {
        try {
            String baseUrl = normalizeEndpoint(config.getApiEndpoint());
            String sessionUrl = baseUrl + "/b1s/v1/\\$metadata";

            HttpHeaders headers = buildAuthHeaders(config);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(sessionUrl, HttpMethod.GET, entity, String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.error("[SAP] Échec test de connexion: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public ErpSyncResult importProducts(ErpConfig config) {
        log.info("[SAP] Import des produits depuis {}", config.getApiEndpoint());
        try {
            String baseUrl = normalizeEndpoint(config.getApiEndpoint());
            String url = baseUrl + "/b1s/v1/Items";

            List<Map<String, Object>> records = fetchAllPages(config, url);
            log.info("[SAP] {} produits importés", records.size());

            return ErpSyncResult.builder()
                    .success(true)
                    .recordsTotal(records.size())
                    .recordsSynced(records.size())
                    .recordsFailed(0)
                    .build();
        } catch (Exception e) {
            log.error("[SAP] Erreur import produits: {}", e.getMessage());
            return ErpSyncResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public ErpSyncResult importOrders(ErpConfig config) {
        log.info("[SAP] Import des commandes depuis {}", config.getApiEndpoint());
        try {
            String baseUrl = normalizeEndpoint(config.getApiEndpoint());
            String url = baseUrl + "/b1s/v1/Orders";

            List<Map<String, Object>> records = fetchAllPages(config, url);
            log.info("[SAP] {} commandes importées", records.size());

            return ErpSyncResult.builder()
                    .success(true)
                    .recordsTotal(records.size())
                    .recordsSynced(records.size())
                    .recordsFailed(0)
                    .build();
        } catch (Exception e) {
            log.error("[SAP] Erreur import commandes: {}", e.getMessage());
            return ErpSyncResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public ErpSyncResult importContacts(ErpConfig config) {
        log.info("[SAP] Import des contacts depuis {}", config.getApiEndpoint());
        try {
            String baseUrl = normalizeEndpoint(config.getApiEndpoint());
            String url = baseUrl + "/b1s/v1/BusinessPartners";

            List<Map<String, Object>> records = fetchAllPages(config, url);
            log.info("[SAP] {} contacts importés", records.size());

            return ErpSyncResult.builder()
                    .success(true)
                    .recordsTotal(records.size())
                    .recordsSynced(records.size())
                    .recordsFailed(0)
                    .build();
        } catch (Exception e) {
            log.error("[SAP] Erreur import contacts: {}", e.getMessage());
            return ErpSyncResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public ErpSyncResult exportShipments(ErpConfig config, List<ShipmentOrder> shipments) {
        log.info("[SAP] Export de {} expéditions vers DeliveryNotes", shipments.size());
        try {
            String baseUrl = normalizeEndpoint(config.getApiEndpoint());
            String url = baseUrl + "/b1s/v1/DeliveryNotes";
            int synced = 0;
            int failed = 0;

            for (ShipmentOrder shipment : shipments) {
                try {
                    Map<String, Object> deliveryNote = new LinkedHashMap<>();
                    deliveryNote.put("CardCode", shipment.getConsigneeName() != null ? shipment.getConsigneeName() : "");
                    deliveryNote.put("DocDate", shipment.getShippedAt() != null ? shipment.getShippedAt().toLocalDate().toString() : null);
                    deliveryNote.put("NumAtCard", shipment.getOrderNumber() != null ? shipment.getOrderNumber() : "");

                    HttpHeaders headers = buildAuthHeaders(config);
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(deliveryNote, headers);
                    ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

                    if (response.getStatusCode().is2xxSuccessful() || response.getStatusCode().value() == 201) {
                        synced++;
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    log.warn("[SAP] Échec export shipment {}: {}", shipment.getOrderNumber(), e.getMessage());
                    failed++;
                }
            }

            log.info("[SAP] {} expéditions exportées, {} échouées", synced, failed);
            return ErpSyncResult.builder()
                    .success(failed == 0)
                    .recordsTotal(shipments.size())
                    .recordsSynced(synced)
                    .recordsFailed(failed)
                    .build();
        } catch (Exception e) {
            log.error("[SAP] Erreur export expéditions: {}", e.getMessage());
            return ErpSyncResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public ErpSyncResult exportOrders(ErpConfig config, List<ShipmentOrder> orders) {
        log.info("[SAP] Export de {} commandes vers SalesOrders", orders.size());
        try {
            String baseUrl = normalizeEndpoint(config.getApiEndpoint());
            String url = baseUrl + "/b1s/v1/Orders";
            int synced = 0;
            int failed = 0;

            for (ShipmentOrder order : orders) {
                try {
                    Map<String, Object> sapOrder = new LinkedHashMap<>();
                    sapOrder.put("CardCode", order.getConsigneeName() != null ? order.getConsigneeName() : "");
                    sapOrder.put("DocDate", order.getBookedAt() != null ? order.getBookedAt().toLocalDate().toString() : null);
                    sapOrder.put("NumAtCard", order.getOrderNumber() != null ? order.getOrderNumber() : "");

                    HttpHeaders headers = buildAuthHeaders(config);
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(sapOrder, headers);
                    ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

                    if (response.getStatusCode().is2xxSuccessful() || response.getStatusCode().value() == 201) {
                        synced++;
                    } else {
                        failed++;
                    }
                } catch (Exception e) {
                    log.warn("[SAP] Échec export commande {}: {}", order.getOrderNumber(), e.getMessage());
                    failed++;
                }
            }

            log.info("[SAP] {} commandes exportées, {} échouées", synced, failed);
            return ErpSyncResult.builder()
                    .success(failed == 0)
                    .recordsTotal(orders.size())
                    .recordsSynced(synced)
                    .recordsFailed(failed)
                    .build();
        } catch (Exception e) {
            log.error("[SAP] Erreur export commandes: {}", e.getMessage());
            return ErpSyncResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public List<Map<String, Object>> getProducts(ErpConfig config) {
        try {
            String baseUrl = normalizeEndpoint(config.getApiEndpoint());
            return fetchAllPages(config, baseUrl + "/b1s/v1/Items");
        } catch (Exception e) {
            log.error("[SAP] Erreur récupération produits: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<Map<String, Object>> getOrders(ErpConfig config) {
        try {
            String baseUrl = normalizeEndpoint(config.getApiEndpoint());
            return fetchAllPages(config, baseUrl + "/b1s/v1/Orders");
        } catch (Exception e) {
            log.error("[SAP] Erreur récupération commandes: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<Map<String, Object>> getContacts(ErpConfig config) {
        try {
            String baseUrl = normalizeEndpoint(config.getApiEndpoint());
            return fetchAllPages(config, baseUrl + "/b1s/v1/BusinessPartners");
        } catch (Exception e) {
            log.error("[SAP] Erreur récupération contacts: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> fetchAllPages(ErpConfig config, String baseUrl) {
        List<Map<String, Object>> allRecords = new ArrayList<>();
        String url = baseUrl;

        while (url != null) {
            HttpHeaders headers = buildAuthHeaders(config);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
            Map body = response.getBody();

            if (response.getStatusCode().is2xxSuccessful() && body != null) {
                Object value = body.get("value");
                if (value instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> map) {
                            Map<String, Object> record = new LinkedHashMap<>();
                            map.forEach((k, v) -> record.put(String.valueOf(k), v));
                            allRecords.add(record);
                        }
                    }
                }
                String nextLink = (String) body.get("odata.nextLink");
                url = nextLink;
            } else {
                url = null;
            }
        }

        return allRecords;
    }

    private HttpHeaders buildAuthHeaders(ErpConfig config) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (config.getUsername() != null && config.getApiSecret() != null) {
            headers.setBasicAuth(config.getUsername(), config.getApiSecret());
        }
        if (config.getDatabaseName() != null) {
            headers.set("CompanyDB", config.getDatabaseName());
        }
        return headers;
    }

    private String normalizeEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new RuntimeException("Endpoint SAP non configuré");
        }
        return endpoint.replaceAll("/$", "");
    }
}
