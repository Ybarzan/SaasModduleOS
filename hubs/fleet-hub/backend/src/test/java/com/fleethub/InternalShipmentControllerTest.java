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

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Lecture d'expéditions par compliance-engine / Logistix via
 * X-Internal-Integration-Key : authentification machine-à-machine (pas un
 * JWT), isolation entre sociétés (la clé d'une société ne donne jamais accès
 * aux expéditions d'une autre).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class InternalShipmentControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String registerAndGetToken(String companyName) throws Exception {
        String email = companyName.toLowerCase().replace(" ", "-") + "-" + System.nanoTime() + "@test.fr";
        MvcResult res = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"" + companyName + "\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private String enableIntegrationAndGetKey(String token) throws Exception {
        MvcResult res = mvc.perform(post("/api/internal-integrations/enable").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("internalIntegrationApiKey").asText();
    }

    private void enableShipmentCreation(String token) throws Exception {
        mvc.perform(post("/api/internal-integrations/shipment-creation/enable").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String createShipment(String token) throws Exception {
        MvcResult res = mvc.perform(post("/api/shipments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shipperName\":\"Shipper SARL\",\"shipperCountry\":\"FR\","
                                + "\"consigneeName\":\"Consignee Ltd\",\"consigneeCountry\":\"GB\","
                                + "\"goodsDescription\":\"Textiles\",\"currency\":\"EUR\","
                                + "\"items\":[{\"sku\":\"SKU-1\",\"name\":\"Widget\",\"hsCode\":\"8471.30\","
                                + "\"quantity\":10,\"unit\":\"PCS\",\"unitPrice\":5}]}"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        return body.get("id").asText();
    }

    @Test
    void getShipment_withoutKeyHeader_returns401() throws Exception {
        mvc.perform(get("/api/internal/shipments/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getShipment_withWrongKey_returns401() throws Exception {
        mvc.perform(get("/api/internal/shipments/" + UUID.randomUUID())
                        .header("X-Internal-Integration-Key", "clef-incorrecte"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getShipment_withoutEnablingFirst_returns401EvenWithRealCompany() throws Exception {
        String token = registerAndGetToken("No Integ Co");
        String shipmentId = createShipment(token);

        // Aucune clé n'a jamais été générée pour cette société -- toute valeur est invalide.
        mvc.perform(get("/api/internal/shipments/" + shipmentId)
                        .header("X-Internal-Integration-Key", "n-importe-quoi"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getShipment_withValidKey_returnsShipmentAndItems() throws Exception {
        String token = registerAndGetToken("Shipper Co");
        String key = enableIntegrationAndGetKey(token);
        String shipmentId = createShipment(token);

        mvc.perform(get("/api/internal/shipments/" + shipmentId)
                        .header("X-Internal-Integration-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shipperName").value("Shipper SARL"))
                .andExpect(jsonPath("$.consigneeName").value("Consignee Ltd"));

        mvc.perform(get("/api/internal/shipments/" + shipmentId + "/items")
                        .header("X-Internal-Integration-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("SKU-1"))
                .andExpect(jsonPath("$[0].quantity").value(10));
    }

    @Test
    void getShipment_withAnotherCompanysKey_returns404NotLeaked() throws Exception {
        String ownerToken = registerAndGetToken("Owner Co");
        String shipmentId = createShipment(ownerToken);

        String otherToken = registerAndGetToken("Other Co");
        String otherKey = enableIntegrationAndGetKey(otherToken);

        mvc.perform(get("/api/internal/shipments/" + shipmentId)
                        .header("X-Internal-Integration-Key", otherKey))
                .andExpect(status().isNotFound());
    }

    private static final String VALID_CREATION_BODY =
            "{\"shipperName\":\"Boutique SARL\",\"shipperCity\":\"Lyon\",\"shipperCountry\":\"FR\","
                    + "\"consigneeName\":\"Client Final\",\"consigneeAddress\":\"12 rue des Lilas\","
                    + "\"consigneeCity\":\"Bruxelles\",\"consigneeCountry\":\"BE\","
                    + "\"goodsDescription\":\"Commande e-commerce\",\"weightKg\":2.5}";

    @Test
    void createFromExternalSource_withoutKeyHeader_returns401() throws Exception {
        mvc.perform(post("/api/internal/shipments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATION_BODY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createFromExternalSource_readOnlyKey_withoutShipmentCreationOptIn_returns403() throws Exception {
        String token = registerAndGetToken("Read Only Co");
        String key = enableIntegrationAndGetKey(token);

        mvc.perform(post("/api/internal/shipments")
                        .header("X-Internal-Integration-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATION_BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void createFromExternalSource_bothOptInsEnabled_createsDraftShipmentWithoutInternalForeignKeys() throws Exception {
        String token = registerAndGetToken("Writer Co");
        String key = enableIntegrationAndGetKey(token);
        enableShipmentCreation(token);

        MvcResult res = mvc.perform(post("/api/internal/shipments")
                        .header("X-Internal-Integration-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATION_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.shipperName").value("Boutique SARL"))
                .andExpect(jsonPath("$.consigneeName").value("Client Final"))
                .andExpect(jsonPath("$.carrier").doesNotExist())
                .andExpect(jsonPath("$.truck").doesNotExist())
                .andReturn();

        // Visible par le propriétaire via la même lecture M2M que les expéditions créées depuis l'UI.
        String shipmentId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/api/internal/shipments/" + shipmentId)
                        .header("X-Internal-Integration-Key", key))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void createFromExternalSource_missingConsigneeName_returns400() throws Exception {
        String token = registerAndGetToken("Invalid Body Co");
        String key = enableIntegrationAndGetKey(token);
        enableShipmentCreation(token);

        mvc.perform(post("/api/internal/shipments")
                        .header("X-Internal-Integration-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"shipperName\":\"Boutique SARL\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createFromExternalSource_disablingShipmentCreationOnly_keepsReadAccessButBlocksCreation() throws Exception {
        String token = registerAndGetToken("Toggle Co");
        String key = enableIntegrationAndGetKey(token);
        enableShipmentCreation(token);

        mvc.perform(post("/api/internal-integrations/shipment-creation/disable").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mvc.perform(post("/api/internal/shipments")
                        .header("X-Internal-Integration-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_CREATION_BODY))
                .andExpect(status().isForbidden());

        // La lecture, elle, reste fonctionnelle : seule la création a été désactivée.
        String shipmentId = createShipment(token);
        mvc.perform(get("/api/internal/shipments/" + shipmentId)
                        .header("X-Internal-Integration-Key", key))
                .andExpect(status().isOk());
    }
}
