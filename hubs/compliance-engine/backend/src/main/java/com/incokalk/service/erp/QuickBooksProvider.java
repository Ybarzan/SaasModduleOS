package com.incokalk.service.erp;

import com.incokalk.model.ErpConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class QuickBooksProvider implements ErpProvider {

    private final RestTemplate restTemplate;

    private static final String QBO_BASE_URL = "https://quickbooks.api.intuit.com/v3/company";

    @Override
    public String getErpType() {
        return "QUICKBOOKS";
    }

    @Override
    public String getName() {
        return "QuickBooks Online";
    }

    @Override
    public boolean testConnection(ErpConfig config) {
        try {
            String companyId = getCompanyId(config);
            String url = QBO_BASE_URL + "/" + companyId + "/query?query=SELECT * FROM CompanyInfo MAXRESULTS 1";

            HttpHeaders headers = buildAuthHeaders(config);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            return response.getStatusCode().is2xxSuccessful() && response.getBody() != null;
        } catch (Exception e) {
            log.error("[QuickBooks] Échec test de connexion: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public ErpSyncResult importProducts(ErpConfig config) {
        log.info("[QuickBooks] Import des produits depuis {}", config.getApiEndpoint());
        try {
            String companyId = getCompanyId(config);
            String url = QBO_BASE_URL + "/" + companyId + "/query?query=SELECT * FROM Item MAXRESULTS 1000";

            List<Map<String, Object>> records = fetchQueryResults(config, url);
            log.info("[QuickBooks] {} produits importés", records.size());

            return ErpSyncResult.builder()
                    .success(true)
                    .recordsTotal(records.size())
                    .recordsSynced(records.size())
                    .recordsFailed(0)
                    .build();
        } catch (Exception e) {
            log.error("[QuickBooks] Erreur import produits: {}", e.getMessage());
            return ErpSyncResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public ErpSyncResult importOrders(ErpConfig config) {
        log.info("[QuickBooks] Import des commandes depuis {}", config.getApiEndpoint());
        try {
            String companyId = getCompanyId(config);
            String url = QBO_BASE_URL + "/" + companyId + "/query?query=SELECT * FROM SalesOrder MAXRESULTS 1000";

            List<Map<String, Object>> records = fetchQueryResults(config, url);
            log.info("[QuickBooks] {} commandes importées", records.size());

            return ErpSyncResult.builder()
                    .success(true)
                    .recordsTotal(records.size())
                    .recordsSynced(records.size())
                    .recordsFailed(0)
                    .build();
        } catch (Exception e) {
            log.error("[QuickBooks] Erreur import commandes: {}", e.getMessage());
            return ErpSyncResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public ErpSyncResult importContacts(ErpConfig config) {
        log.info("[QuickBooks] Import des contacts depuis {}", config.getApiEndpoint());
        try {
            String companyId = getCompanyId(config);
            String url = QBO_BASE_URL + "/" + companyId + "/query?query=SELECT * FROM Customer MAXRESULTS 1000";

            List<Map<String, Object>> records = fetchQueryResults(config, url);
            log.info("[QuickBooks] {} contacts importés", records.size());

            return ErpSyncResult.builder()
                    .success(true)
                    .recordsTotal(records.size())
                    .recordsSynced(records.size())
                    .recordsFailed(0)
                    .build();
        } catch (Exception e) {
            log.error("[QuickBooks] Erreur import contacts: {}", e.getMessage());
            return ErpSyncResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    // exportShipments/exportOrders ont ete retirees avec le domaine Transport
    // (List<ShipmentOrder>). Voir ErpProvider / ErpSyncService.

    @Override
    public List<Map<String, Object>> getProducts(ErpConfig config) {
        try {
            String companyId = getCompanyId(config);
            return fetchQueryResults(config, QBO_BASE_URL + "/" + companyId + "/query?query=SELECT * FROM Item MAXRESULTS 1000");
        } catch (Exception e) {
            log.error("[QuickBooks] Erreur récupération produits: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<Map<String, Object>> getOrders(ErpConfig config) {
        try {
            String companyId = getCompanyId(config);
            return fetchQueryResults(config, QBO_BASE_URL + "/" + companyId + "/query?query=SELECT * FROM SalesOrder MAXRESULTS 1000");
        } catch (Exception e) {
            log.error("[QuickBooks] Erreur récupération commandes: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<Map<String, Object>> getContacts(ErpConfig config) {
        try {
            String companyId = getCompanyId(config);
            return fetchQueryResults(config, QBO_BASE_URL + "/" + companyId + "/query?query=SELECT * FROM Customer MAXRESULTS 1000");
        } catch (Exception e) {
            log.error("[QuickBooks] Erreur récupération contacts: {}", e.getMessage());
            return List.of();
        }
    }

    private List<Map<String, Object>> fetchQueryResults(ErpConfig config, String url) {
        List<Map<String, Object>> allRecords = new ArrayList<>();
        String currentUrl = url;

        while (currentUrl != null) {
            HttpHeaders headers = buildAuthHeaders(config);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(currentUrl, HttpMethod.GET, entity, Map.class);
            Map body = response.getBody();

            if (response.getStatusCode().is2xxSuccessful() && body != null) {
                Object queryResponse = body.get("QueryResponse");
                if (queryResponse instanceof Map<?, ?> qr) {
                    for (String key : List.of("Item", "SalesOrder", "Customer")) {
                        Object items = qr.get(key);
                        if (items instanceof List<?> list) {
                            for (Object item : list) {
                                if (item instanceof Map<?, ?> map) {
                                    Map<String, Object> record = new LinkedHashMap<>();
                                    map.forEach((k, v) -> record.put(String.valueOf(k), v));
                                    allRecords.add(record);
                                }
                            }
                        }
                    }
                }

                Object deformationToken = body.get("deformationContext");
                String nextQuery = null;
                if (queryResponse instanceof Map<?, ?> qr) {
                    Object nq = qr.get("nextQuery");
                    if (nq instanceof String s) nextQuery = s;
                }
                currentUrl = nextQuery;
            } else {
                currentUrl = null;
            }
        }

        return allRecords;
    }

    private String getCompanyId(ErpConfig config) {
        if (config.getApiEndpoint() == null || config.getApiEndpoint().isBlank()) {
            throw new RuntimeException("Company ID QuickBooks non configuré (apiEndpoint)");
        }
        return config.getApiEndpoint();
    }

    private HttpHeaders buildAuthHeaders(ErpConfig config) {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        if (config.getApiKey() != null) {
            headers.setBearerAuth(config.getApiKey());
        }
        return headers;
    }
}
