package com.fleethub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Contrat JSON de {@code /api/internal/shipments/**} tel que le lisent les autres
 * hubs. Les listes de champs ci-dessous recopient les DTO des consommateurs :
 * <ul>
 *   <li>compliance-engine : {@code ShipmentDetailDto}, {@code ShipmentSummary},
 *       {@code RecentShipmentDto}, {@code ShipmentItemDetailDto}</li>
 *   <li>logistix : {@code ShipmentSearchResult}, {@code ShipmentItemSummary}</li>
 *   <li>finance : {@code ShipmentLaneSummary}</li>
 * </ul>
 * Retirer ou renommer l'un de ces champs casse un autre hub sans erreur de
 * compilation : ce test est le garde-fou. Ajouter un champ est sans risque
 * (les consommateurs ignorent les champs inconnus).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InternalApiContractTest {

    /** Champs lus sur GET /api/internal/shipments/{id} (détail). */
    static final List<String> DETAIL_FIELDS = List.of(
            "id", "orderNumber", "status",
            "shipperName", "shipperAddress", "shipperCity", "shipperCountry", "shipperPostalCode",
            "consigneeName", "consigneeAddress", "consigneeCity", "consigneeCountry", "consigneePostalCode",
            "goodsDescription", "goodsValue", "currency", "weightKg", "volumeM3", "packagesCount",
            "hsCode", "incotermCode", "dangerous", "countryOfOrigin", "containerType",
            "carrierId", "carrierName", "transportMode", "customsStatus", "customsDeclarationNumber",
            "dutyAmount", "vatAmount", "landedCost", "quotedCost", "finalCost", "costCurrency",
            "requestedPickupDate", "estimatedDeliveryDate", "actualDeliveryDate", "bookedAt", "shippedAt",
            "createdAt", "trackingEvents");

    /** Champs lus sur GET /api/internal/shipments (liste et recherche). */
    static final List<String> LIST_FIELDS = List.of(
            "id", "orderNumber", "status", "shipperCity", "shipperCountry", "consigneeCity", "consigneeCountry",
            "goodsDescription", "weightKg", "incotermCode", "quotedCost", "finalCost", "currency",
            "carrierName", "transportMode", "createdAt", "estimatedDeliveryDate");

    /** Champs lus sur GET /api/internal/shipments/{id}/items. */
    static final List<String> ITEM_FIELDS = List.of(
            "id", "sku", "name", "description", "hsCode", "originCountry", "quantity", "unit", "unitPrice");

    /** Champs lus sur chaque événement de suivi du détail. */
    static final List<String> TRACKING_EVENT_FIELDS = List.of(
            "id", "status", "location", "description", "eventTime", "source");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private String apiKey;
    private String shipmentId;

    private JsonNode postJson(String url, String body) throws Exception {
        MvcResult res = mvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    @BeforeEach
    void setUp() throws Exception {
        String email = "contract-" + System.nanoTime() + "@test.fr";
        MvcResult reg = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Contract\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();
        postJson("/api/internal-integrations/enable", "");
        apiKey = postJson("/api/internal-integrations/keys/COMPLIANCE_ENGINE", "").get("apiKey").asText();

        String truckId = postJson("/api/trucks", "{\"registration\":\"CT-" + (System.nanoTime() % 1000)
                + "-AB\",\"brand\":\"Renault\",\"model\":\"Master\",\"truckType\":\"VUL\",\"fuelType\":\"DIESEL\"}")
                .get("id").asText();
        String carrierId = postJson("/api/carriers", "{\"name\":\"Transports Test\",\"code\":\"TT"
                + (System.nanoTime() % 100000) + "\",\"transportModes\":\"ROAD\"}").get("id").asText();
        shipmentId = postJson("/api/shipments", "{\"shipperName\":\"Labo Lyon\",\"shipperCountry\":\"FR\","
                + "\"consigneeName\":\"Plateau technique\",\"consigneeCountry\":\"FR\",\"goodsDescription\":\"Échantillons\","
                + "\"currency\":\"EUR\",\"truckId\":" + truckId + ",\"carrierId\":\"" + carrierId + "\","
                + "\"items\":[{\"sku\":\"TUBE-1\",\"name\":\"Tube EDTA\",\"hsCode\":\"3822.19\",\"quantity\":12,"
                + "\"unit\":\"PCS\",\"unitPrice\":0.5}]}").get("id").asText();
    }

    private JsonNode internalGet(String url) throws Exception {
        MvcResult res = mvc.perform(get(url).header("X-Internal-Integration-Key", apiKey))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private static void assertHasFields(JsonNode node, List<String> fields, String what) {
        for (String f : fields) {
            assertTrue(node.has(f), what + " : champ « " + f + " » absent du JSON (lu par un autre hub)");
        }
    }

    @Test
    void detail_ofAShipmentWithTruckAndCarrier_exposesTheConsumersFields() throws Exception {
        JsonNode detail = internalGet("/api/internal/shipments/" + shipmentId);
        assertHasFields(detail, DETAIL_FIELDS, "détail");
        assertEquals("Transports Test", detail.get("carrierName").asText());
    }

    @Test
    void list_andSearch_exposeTheConsumersFields() throws Exception {
        JsonNode list = internalGet("/api/internal/shipments");
        assertEquals(1, list.size());
        assertHasFields(list.get(0), LIST_FIELDS, "liste");

        JsonNode search = internalGet("/api/internal/shipments?q=Plateau");
        assertEquals(1, search.size());
        assertHasFields(search.get(0), LIST_FIELDS, "recherche");
    }

    @Test
    void items_exposeTheConsumersFields() throws Exception {
        JsonNode items = internalGet("/api/internal/shipments/" + shipmentId + "/items");
        assertEquals(1, items.size());
        assertHasFields(items.get(0), ITEM_FIELDS, "article");
        assertEquals("TUBE-1", items.get(0).get("sku").asText());
    }

    @Test
    void detail_doesNotLeakInternalFields() throws Exception {
        JsonNode detail = internalGet("/api/internal/shipments/" + shipmentId);
        for (String internal : List.of("truck", "company", "user", "shippingRate", "clientId")) {
            assertTrue(!detail.has(internal), "champ interne « " + internal + " » exposé aux autres hubs");
        }
    }
}
