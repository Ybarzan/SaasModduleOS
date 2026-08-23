package com.fleethub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleethub.model.Driver;
import com.fleethub.model.Truck;
import com.fleethub.repository.DriverRepository;
import com.fleethub.repository.TruckRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Page « Intégrations » self-service : enregistrement d'un fournisseur
 * (clé chiffrée, jamais renvoyée), test de connexion, et canal de push
 * authentifié par la clé de webhook de la société (données tenant-scopées).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IntegrationConfigTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TruckRepository truckRepository;
    @Autowired
    private DriverRepository driverRepository;

    private JsonNode register(String email, String company) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"" + company + "\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    private String createConfig(String token, String baseUrl, String apiKey) throws Exception {
        MvcResult res = mvc.perform(post("/api/integrations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"DHL\",\"baseUrl\":\"" + baseUrl
                                + "\",\"apiKey\":\"" + apiKey + "\",\"enabled\":true}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void crudMasksApiKeyAndTestsConnection() throws Exception {
        String token = register("admin@integ.fr", "Integ Co").get("token").asText();

        // Création : la clé n'est jamais renvoyée en clair.
        MvcResult created = mvc.perform(post("/api/integrations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"GPS_WEBFLEET\",\"baseUrl\":\"http://127.0.0.1:1\","
                                + "\"apiKey\":\"secret-cle-1234\",\"enabled\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasApiKey").value(true))
                .andExpect(jsonPath("$.apiKeyMasked").value("••••1234"))
                .andExpect(jsonPath("$.webhookKey").isNotEmpty())
                .andReturn();
        String body = created.getResponse().getContentAsString();
        assertFalse(body.contains("secret-cle-1234"), "La clé API ne doit pas être renvoyée en clair");
        String id = objectMapper.readTree(body).get("id").asText();

        // Listing : toujours masquée.
        mvc.perform(get("/api/integrations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].apiKeyMasked").value("••••1234"));

        // Test de connexion (port fermé -> échec rapide, pas d'exception).
        mvc.perform(post("/api/integrations/" + id + "/test")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(false));

        // Mise à jour de la clé puis suppression de la clé (conserve l'ancienne).
        mvc.perform(put("/api/integrations/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"GPS_WEBFLEET\",\"baseUrl\":\"http://127.0.0.1:1\","
                                + "\"apiKey\":\"nouvelle-cle-9999\",\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKeyMasked").value("••••9999"));
        mvc.perform(put("/api/integrations/" + id)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"GPS_WEBFLEET\",\"baseUrl\":\"http://127.0.0.1:1\","
                                + "\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiKeyMasked").value("••••9999"));

        // Suppression.
        mvc.perform(delete("/api/integrations/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/integrations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void webhookPushIsTenantScopedAndAuthenticatedByCompanyKey() throws Exception {
        JsonNode first = register("admin@wh1.fr", "Webhook One");
        String token = first.get("token").asText();
        long companyId = first.get("companyId").asLong();

        // Camion + chauffeur du tenant.
        mvc.perform(post("/api/trucks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registration\":\"WB-111-AA\",\"brand\":\"Renault\",\"model\":\"T520\","
                                + "\"truckType\":\"TRACTEUR\",\"fuelType\":\"DIESEL\","
                                + "\"expectedConsumptionL100Km\":32.0,\"active\":true}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/drivers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jean\",\"lastName\":\"Dupont\","
                                + "\"licenseNumber\":\"L-WH-1\",\"phone\":\"0600000000\","
                                + "\"email\":\"jean@wh1.fr\",\"active\":true}"))
                .andExpect(status().isOk());

        // Configuration d'intégration -> récupère la clé de webhook.
        String id = createConfig(token, "http://127.0.0.1:1", "wh-key");
        String webhookKey = objectMapper.readTree(mvc.perform(get("/api/integrations")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get(0).get("webhookKey").asText();

        String payload = "{\"positions\":[{\"registration\":\"WB-111-AA\",\"latitude\":45.0,\"longitude\":4.0,"
                + "\"speedKph\":60.0,\"timestamp\":\"2026-08-10T17:00:00\"}],"
                + "\"tachographDays\":[{\"licenseNumber\":\"L-WH-1\",\"date\":\"" + LocalDate.now()
                + "\",\"drivingHours\":8.0,\"workHours\":10,\"restMinutes\":480,\"compliant\":true}],"
                + "\"fuelTransactions\":[{\"registration\":\"WB-111-AA\",\"date\":\"" + LocalDate.now()
                + "\",\"liters\":100.0,\"amount\":180.0,\"odometerKm\":10000.0}]}";

        // Clé inconnue -> 401.
        mvc.perform(post("/api/webhooks/ingest")
                        .header("X-API-Key", "clé-inconnue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isUnauthorized());

        // Clé de la société -> push tenant-scopé.
        mvc.perform(post("/api/webhooks/ingest")
                        .header("X-API-Key", webhookKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("company-webhook"))
                .andExpect(jsonPath("$.positionsUpdated").value(1))
                .andExpect(jsonPath("$.tachographDaysSaved").value(1))
                .andExpect(jsonPath("$.fuelTransactionsSaved").value(1));

        Truck truck = truckRepository.findByRegistrationAndCompanyId("WB-111-AA", companyId).orElseThrow();
        assertEquals(45.0, truck.getCurrentLatitude(), 0.0001);

        // Isolation : une autre société ne voit pas ces données via sa clé.
        JsonNode other = register("admin@wh2.fr", "Webhook Two");
        String otherToken = other.get("token").asText();
        String otherId = createConfig(otherToken, "http://127.0.0.1:1", "other-key");
        String otherKey = objectMapper.readTree(mvc.perform(get("/api/integrations")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString()).get(0).get("webhookKey").asText();

        mvc.perform(post("/api/webhooks/ingest")
                        .header("X-API-Key", otherKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionsUpdated").value(0))
                .andExpect(jsonPath("$.tachographDaysSaved").value(0))
                .andExpect(jsonPath("$.fuelTransactionsSaved").value(0));
    }
}
