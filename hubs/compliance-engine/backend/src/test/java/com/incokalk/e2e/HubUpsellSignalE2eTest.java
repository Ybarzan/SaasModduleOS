package com.incokalk.e2e;

import com.incokalk.model.Company;
import org.junit.jupiter.api.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifie le declencheur d'upsell bout en bout, a travers la vraie API
 * (POST /v1/roles -> GET /v1/notifications), pas seulement le service isole
 * (deja couvert par HubUpsellSignalServiceTest).
 */
public class HubUpsellSignalE2eTest extends E2eTestBase {

    private Map<String, Object> createRoleBody(String name, String description) {
        var body = new LinkedHashMap<String, Object>();
        body.put("name", name);
        body.put("description", description);
        body.put("permissions", List.of());
        return body;
    }

    @Test
    @DisplayName("Creer un role 'Douane' sous plan Starter -> une notification d'upsell apparait")
    void creatingCustomsRole_underStarterPlan_triggersUpsellNotification() {
        registerAndSetToken(); // upgrade automatique a STARTER (E2eTestBase)

        var createResp = post("/v1/roles", createRoleBody("Douane", "Gere les declarations et l'EORI"));
        assertTrue(createResp.getStatusCode().is2xxSuccessful(), "creation du role attendue en succes");

        var notifResp = getList("/v1/notifications");
        assertTrue(notifResp.getStatusCode().is2xxSuccessful());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> notifications = notifResp.getBody();
        assertNotNull(notifications);

        boolean found = notifications.stream()
            .anyMatch(n -> "HUB_UPSELL_SIGNAL".equals(n.get("eventType")));
        assertTrue(found, "une notification HUB_UPSELL_SIGNAL est attendue apres creation du role Douane");
    }

    @Test
    @DisplayName("Creer un role 'Douane' sous plan Croissance (deja suffisant) -> pas de notification")
    void creatingCustomsRole_underProPlan_noUpsellNotification() {
        registerAndSetToken();
        upgradeCompanyPlan(Company.Plan.PRO);

        var createResp = post("/v1/roles", createRoleBody("Douane", "Gere les declarations"));
        assertTrue(createResp.getStatusCode().is2xxSuccessful());

        var notifResp = getList("/v1/notifications");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> notifications = notifResp.getBody();
        assertNotNull(notifications);

        boolean found = notifications.stream()
            .anyMatch(n -> "HUB_UPSELL_SIGNAL".equals(n.get("eventType")));
        assertFalse(found, "aucune notification attendue, le plan Croissance couvre deja la douane approfondie");
    }

    @Test
    @DisplayName("Creer un role sans mot-cle connu -> pas de notification")
    void creatingUnrelatedRole_noUpsellNotification() {
        registerAndSetToken();

        var createResp = post("/v1/roles", createRoleBody("Support Client", "Repond aux tickets"));
        assertTrue(createResp.getStatusCode().is2xxSuccessful());

        var notifResp = getList("/v1/notifications");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> notifications = notifResp.getBody();
        assertNotNull(notifications);

        boolean found = notifications.stream()
            .anyMatch(n -> "HUB_UPSELL_SIGNAL".equals(n.get("eventType")));
        assertFalse(found);
    }
}
