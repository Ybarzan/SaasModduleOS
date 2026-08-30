package com.incokalk.e2e;

import com.incokalk.model.Company;
import com.incokalk.service.EventOutboxProcessor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Preuve de bout en bout que la Phase 3 (docs/03-plan-migration.md) tient
 * assemblée à travers de vrais appels HTTP + une vraie base H2, pas seulement
 * à travers des tests unitaires qui mockent chaque maillon séparément :
 * création de règle -> vrai changement de statut -> vraie écriture outbox ->
 * vrai traitement -> vraie suggestion -> vraie décision -> vrai résultat
 * d'exécution persisté et relisible par API.
 *
 * Ne mocke aucun ErpProvider : les scénarios choisis (aucune config ERP
 * active, budget dépassé) échouent avant tout appel ERP, donc ils prouvent
 * le câblage réel de bout en bout sans dépendre d'un connecteur externe.
 * Le succès de l'appel ErpProvider.exportOrders lui-même est déjà couvert en
 * isolation par OrchestrationExecutorTest.
 */
class OrchestrationSuggestionE2eTest extends E2eTestBase {

    @Autowired
    private EventOutboxProcessor eventOutboxProcessor;

    private String createShipment(double quotedCost) {
        var body = new LinkedHashMap<String, Object>();
        body.put("shipperName", "E2E Shipper");
        body.put("consigneeName", "E2E Consignee");
        body.put("origin", "Paris");
        body.put("destination", "New York");
        body.put("incotermCode", "CIF");
        body.put("goodsDescription", "Electronics");
        body.put("goodsValue", 10000.00);
        body.put("weightKg", 500.5);
        body.put("quotedCost", quotedCost);
        var resp = post("/v1/shipments", body);
        assertTrue(resp.getStatusCode().is2xxSuccessful(), "Échec création expédition: " + resp.getBody());
        return jsonPath(resp, "id").toString();
    }

    private String createRule(String actionType, String filterStatus, Double maxBudgetAmount) {
        var body = new LinkedHashMap<String, Object>();
        body.put("name", "Règle E2E " + actionType);
        body.put("eventType", "SHIPMENT_STATUS_CHANGE");
        body.put("isActive", true);
        body.put("sendInApp", true);
        body.put("filterStatus", filterStatus);
        body.put("actionType", actionType);
        if (maxBudgetAmount != null) body.put("maxBudgetAmount", maxBudgetAmount);
        var resp = post("/v1/notification-rules", body);
        assertTrue(resp.getStatusCode().is2xxSuccessful(), "Échec création règle: " + resp.getBody());
        return jsonPath(resp, "id").toString();
    }

    private void patchStatus(String shipmentId, String status) {
        var body = Map.of("status", status);
        ResponseEntity<Map> resp = restTemplate.exchange(
                baseUrl + "/v1/shipments/" + shipmentId + "/status", HttpMethod.PATCH,
                new HttpEntity<>(body, authHeaders()), Map.class);
        assertTrue(resp.getStatusCode().is2xxSuccessful(), "Échec changement de statut: " + resp.getBody());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listSuggestions(String status) {
        var resp = getList("/v1/orchestration-suggestions?status=" + status);
        assertEquals(200, resp.getStatusCode().value());
        return (List<Map<String, Object>>) (List<?>) resp.getBody();
    }

    @Test
    @DisplayName("Un vrai changement de statut déclenche la création d'une OrchestrationSuggestion PENDING_APPROVAL")
    void statusChange_throughRealHttpAndOutbox_createsPendingSuggestion() {
        registerAndSetToken();
        upgradeCompanyPlan(Company.Plan.PRO);

        String shipmentId = createShipment(2000.0);
        createRule("SUGGEST_ERP_ORDER_ADJUSTMENT", "BOOKED", null);

        patchStatus(shipmentId, "BOOKED");
        eventOutboxProcessor.processPendingEvents();

        List<Map<String, Object>> pending = listSuggestions("PENDING_APPROVAL");
        assertEquals(1, pending.size());
        Map<String, Object> suggestion = pending.get(0);
        assertEquals("SUGGEST_ERP_ORDER_ADJUSTMENT", suggestion.get("actionType"));
        assertEquals(shipmentId, suggestion.get("shipmentId"));
        assertTrue(((String) suggestion.get("ruleName")).startsWith("Règle E2E"));
    }

    @Test
    @DisplayName("Approuver sans configuration ERP active fait échouer l'exécution réelle avec une raison explicite")
    void approve_withoutActiveErpConfig_marksExecutionFailed() {
        registerAndSetToken();
        upgradeCompanyPlan(Company.Plan.PRO);

        String shipmentId = createShipment(2000.0);
        createRule("SUGGEST_ERP_ORDER_ADJUSTMENT", "BOOKED", null);
        patchStatus(shipmentId, "BOOKED");
        eventOutboxProcessor.processPendingEvents();

        String suggestionId = (String) listSuggestions("PENDING_APPROVAL").get(0).get("id");

        var resp = post("/v1/orchestration-suggestions/" + suggestionId + "/approve", Map.of("note", "OK"));
        assertEquals(200, resp.getStatusCode().value());
        assertEquals("FAILED", jsonPath(resp, "status"));
        assertTrue(((String) jsonPath(resp, "executionResult")).contains("Aucune configuration ERP active"));
    }

    @Test
    @DisplayName("Approuver au-delà du budget maximum de la règle bloque l'exécution avant tout appel ERP")
    void approve_costAboveMaxBudget_blockedByGovernanceBeforeErpCall() {
        registerAndSetToken();
        upgradeCompanyPlan(Company.Plan.PRO);

        String shipmentId = createShipment(5000.0);
        createRule("SUGGEST_ERP_ORDER_ADJUSTMENT", "BOOKED", 500.0);
        patchStatus(shipmentId, "BOOKED");
        eventOutboxProcessor.processPendingEvents();

        String suggestionId = (String) listSuggestions("PENDING_APPROVAL").get(0).get("id");

        var resp = post("/v1/orchestration-suggestions/" + suggestionId + "/approve", Map.of());
        assertEquals(200, resp.getStatusCode().value());
        assertEquals("FAILED", jsonPath(resp, "status"));
        assertTrue(((String) jsonPath(resp, "executionResult")).contains("dépasse le budget"));
    }

    @Test
    @DisplayName("Rejeter une suggestion la fait passer à REJECTED sans jamais tenter d'exécution")
    void reject_pendingSuggestion_neverAttemptsExecution() {
        registerAndSetToken();
        upgradeCompanyPlan(Company.Plan.PRO);

        String shipmentId = createShipment(2000.0);
        createRule("SUGGEST_ERP_ORDER_ADJUSTMENT", "BOOKED", null);
        patchStatus(shipmentId, "BOOKED");
        eventOutboxProcessor.processPendingEvents();

        String suggestionId = (String) listSuggestions("PENDING_APPROVAL").get(0).get("id");

        var resp = post("/v1/orchestration-suggestions/" + suggestionId + "/reject", Map.of("note", "Pas nécessaire"));
        assertEquals(200, resp.getStatusCode().value());
        assertEquals("REJECTED", jsonPath(resp, "status"));
        assertNull(jsonPath(resp, "executionResult"));
    }
}
