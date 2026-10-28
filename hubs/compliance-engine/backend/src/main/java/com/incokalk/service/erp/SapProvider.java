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

    // exportShipments/exportOrders ont ete retirees avec le domaine Transport
    // (List<ShipmentOrder>). Voir ErpProvider / ErpSyncService.

    /**
     * Non implémenté, délibérément. L'authentification déjà utilisée par ce
     * connecteur pour les imports (Basic Auth direct sur chaque requête, sans
     * session {@code POST /b1s/v1/Login} préalable) n'a elle-même jamais été
     * vérifiée contre un vrai SAP Business One Service Layer -- construire un
     * export dessus imiterait un mécanisme déjà incertain plutôt qu'une
     * fondation prouvée (contrairement à Odoo/QuickBooks, voir leurs
     * implémentations). Échec explicite, jamais un faux succès.
     */
    @Override
    public ErpExportResult exportAdjustmentNote(ErpConfig config, ErpAdjustmentNote note) {
        return ErpExportResult.fail(
                "Export SAP non implémenté : l'authentification Service Layer utilisée par ce connecteur "
                        + "n'a jamais été vérifiée contre un vrai SAP B1, même pour les imports -- voir le javadoc");
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
