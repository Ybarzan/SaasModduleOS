package com.incokalk.service.erp;

import com.incokalk.model.ErpConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * exportAdjustmentNote() (voir plus bas) n'a jamais été testé contre une
 * vraie instance QuickBooks Online depuis cet environnement (pas de sandbox
 * disponible) -- le mécanisme réutilisé (requête + mise à jour sparse avec
 * SyncToken, entité PurchaseOrder) est en revanche un mécanisme QBO stable et
 * documenté depuis longtemps, pas une forme d'API récemment changée comme
 * celle rencontrée pour Shopify.
 */
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

    /**
     * Cherche une commande fournisseur (PurchaseOrder, entité réelle et stable
     * de l'API QuickBooks Online -- contrairement à "SalesOrder" utilisée par
     * importOrders/getOrders ci-dessus, qui n'existe pas dans l'API QBO) dont
     * le DocNumber correspond à la référence donnée, puis ajoute la note en
     * l'ajoutant (sans écraser l'existant) à son PrivateNote via une mise à
     * jour sparse -- jamais de création de PurchaseOrder de zéro (voir javadoc
     * ErpProvider.exportAdjustmentNote).
     */
    @Override
    public ErpExportResult exportAdjustmentNote(ErpConfig config, ErpAdjustmentNote note) {
        try {
            String companyId = getCompanyId(config);
            String escapedRef = note.reference() == null ? "" : note.reference().replace("'", "''");
            String searchUrl = QBO_BASE_URL + "/" + companyId
                    + "/query?query=SELECT * FROM PurchaseOrder WHERE DocNumber = '" + escapedRef + "' MAXRESULTS 1";

            HttpHeaders searchHeaders = buildAuthHeaders(config);
            ResponseEntity<Map> searchResponse = restTemplate.exchange(
                    searchUrl, HttpMethod.GET, new HttpEntity<>(searchHeaders), Map.class);
            Map<String, Object> purchaseOrder = extractFirstPurchaseOrder(searchResponse.getBody());
            if (purchaseOrder == null) {
                return ErpExportResult.fail(
                        "Aucune commande fournisseur QuickBooks trouvée avec DocNumber=" + note.reference());
            }

            String id = String.valueOf(purchaseOrder.get("Id"));
            String syncToken = String.valueOf(purchaseOrder.get("SyncToken"));
            Object existingNoteObj = purchaseOrder.get("PrivateNote");
            String existingNote = existingNoteObj != null ? existingNoteObj.toString() : "";
            String updatedNote = existingNote.isBlank() ? note.body() : existingNote + "\n---\n" + note.body();
            // PrivateNote est limité à 4000 caractères côté QuickBooks (connaissance
            // d'entraînement, non vérifiée contre une vraie instance depuis cet
            // environnement -- voir le javadoc de classe).
            if (updatedNote.length() > 4000) {
                updatedNote = updatedNote.substring(updatedNote.length() - 4000);
            }

            Map<String, Object> updatePayload = new LinkedHashMap<>();
            updatePayload.put("Id", id);
            updatePayload.put("SyncToken", syncToken);
            updatePayload.put("sparse", true);
            updatePayload.put("PrivateNote", updatedNote);

            HttpHeaders updateHeaders = buildAuthHeaders(config);
            updateHeaders.setContentType(MediaType.APPLICATION_JSON);
            String updateUrl = QBO_BASE_URL + "/" + companyId + "/purchaseorder";
            ResponseEntity<Map> updateResponse = restTemplate.exchange(
                    updateUrl, HttpMethod.POST, new HttpEntity<>(updatePayload, updateHeaders), Map.class);
            if (!updateResponse.getStatusCode().is2xxSuccessful()) {
                return ErpExportResult.fail("Échec de la mise à jour QuickBooks pour la commande " + note.reference());
            }

            log.info("[QuickBooks] Note d'ajustement ajoutée à la commande fournisseur {}", note.reference());
            return ErpExportResult.ok(note.reference(), "Note ajoutée à la commande fournisseur QuickBooks " + note.reference());
        } catch (Exception e) {
            log.error("[QuickBooks] Erreur export ajustement: {}", e.getMessage());
            return ErpExportResult.fail("Erreur QuickBooks: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractFirstPurchaseOrder(Map body) {
        if (body == null) return null;
        Object queryResponse = body.get("QueryResponse");
        if (!(queryResponse instanceof Map<?, ?> qr)) return null;
        Object purchaseOrders = qr.get("PurchaseOrder");
        if (!(purchaseOrders instanceof List<?> list) || list.isEmpty()) return null;
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> map)) return null;
        Map<String, Object> record = new LinkedHashMap<>();
        map.forEach((k, v) -> record.put(String.valueOf(k), v));
        return record;
    }

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
