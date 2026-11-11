package com.fleethub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleethub.model.CustodyEvent;
import com.fleethub.repository.CustodyEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Activité Transport médical : comptage par catégorie, rapprochement collecté /
 * remis, journal de garde chaîné (falsification détectée), non-conformités
 * automatiques et cloisonnement vis-à-vis des autres activités.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MedicalTransportTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CustodyEventRepository custodyRepository;

    private String register(String company, String email, String profile) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"" + company + "\",\"firstName\":\"Qa\",\"lastName\":\"Lité\","
                                + "\"email\":\"" + email + "\",\"password\":\"password123\",\"fleetProfile\":\"" + profile + "\"}"))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private ResultActions call(String token, MockHttpServletRequestBuilder req, String body) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (body != null) {
            req.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mvc.perform(req);
    }

    private JsonNode json(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private long site(String token, String name, String kind, double lon) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"kind\":\"" + kind + "\",\"latitude\":45.76,\"longitude\":" + lon + "}";
        return json(call(token, post("/api/sites"), body).andExpect(status().isOk())).get("id").asLong();
    }

    private long tour(String token, String name) throws Exception {
        return json(call(token, post("/api/tours"),
                "{\"name\":\"" + name + "\",\"date\":\"" + LocalDate.now() + "\"}").andExpect(status().isOk())).get("id").asLong();
    }

    private JsonNode addStop(String token, long tourId, long siteId, String type, String extra) throws Exception {
        return json(call(token, post("/api/tours/" + tourId + "/stops"),
                "{\"siteId\":" + siteId + ",\"type\":\"" + type + "\"" + (extra != null ? "," + extra : "") + "}")
                .andExpect(status().isOk()));
    }

    private List<String> eventTypes(JsonNode events) {
        List<String> types = new ArrayList<>();
        events.forEach(e -> types.add(e.get("type").asText()));
        return types;
    }

    @Test
    void countsReconciliationLedgerAndNonConformities() throws Exception {
        String token = register("TEST-BioNavette", "qualite@bionavette.test", "COLLECTE_SANTE");
        long pharmaA = site(token, "Pharmacie A", "PHARMACIE", 4.84);
        long pharmaB = site(token, "Pharmacie B", "PHARMACIE", 4.85);
        long lab = site(token, "Laboratoire Central", "LABORATOIRE", 4.86);
        long tourId = tour(token, "Collecte matin");
        addStop(token, tourId, pharmaA, "COLLECTE", "\"temperatureMinCelsius\":2,\"temperatureMaxCelsius\":8");
        addStop(token, tourId, pharmaB, "COLLECTE", "\"temperatureMinCelsius\":2,\"temperatureMaxCelsius\":8");
        JsonNode planned = addStop(token, tourId, lab, "LIVRAISON", null);
        assertTrue(planned.get("medical").asBoolean());
        long stopA = planned.get("stops").get(0).get("id").asLong();
        long stopB = planned.get("stops").get(1).get("id").asLong();
        long stopLab = planned.get("stops").get(2).get("id").asLong();

        // Prise en charge à la pharmacie A : quantités par catégorie, position du téléphone
        String proofA = "{\"status\":\"FAIT\",\"signedBy\":\"Mme Durand\",\"temperatureCelsius\":5,"
                + "\"products\":{\"SANG\":3,\"PCR\":2},\"latitude\":45.76,\"longitude\":4.84}";
        call(token, post("/api/tours/" + tourId + "/stops/" + stopA + "/complete"), proofA)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EN_COURS"))
                .andExpect(jsonPath("$.stops[0].sampleCount").value(5))
                .andExpect(jsonPath("$.stops[0].products.SANG").value(3));
        long eventsAfterA = custodyRepository.findByCompanyIdAndTourIdOrderBySequenceNoAsc(companyOf(tourId), tourId).size();

        // Renvoi identique (file hors ligne) : idempotent, rien de plus au journal
        call(token, post("/api/tours/" + tourId + "/stops/" + stopA + "/complete"), proofA).andExpect(status().isOk());
        assertEquals(eventsAfterA, custodyRepository.findByCompanyIdAndTourIdOrderBySequenceNoAsc(companyOf(tourId), tourId).size());

        // Correction d'un passage clôturé : motif obligatoire, tracée comme CORRECTION
        String corrected = "{\"status\":\"FAIT\",\"signedBy\":\"Mme Durand\",\"temperatureCelsius\":5,"
                + "\"products\":{\"SANG\":4,\"PCR\":2}";
        call(token, post("/api/tours/" + tourId + "/stops/" + stopA + "/complete"), corrected + "}")
                .andExpect(status().isBadRequest());
        call(token, post("/api/tours/" + tourId + "/stops/" + stopA + "/complete"),
                corrected + ",\"correctionReason\":\"Un tube oublié dans le sachet\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops[0].sampleCount").value(6));

        // Catégorie inconnue refusée
        call(token, post("/api/tours/" + tourId + "/stops/" + stopB + "/complete"),
                "{\"status\":\"FAIT\",\"products\":{\"PLASMA\":1}}").andExpect(status().isBadRequest());

        // Pharmacie B relevée à 11 °C : rupture de la chaîne du froid
        JsonNode afterB = json(call(token, post("/api/tours/" + tourId + "/stops/" + stopB + "/complete"),
                "{\"status\":\"FAIT\",\"temperatureCelsius\":11,\"products\":{\"URINE\":1}}").andExpect(status().isOk()));
        // La remise au laboratoire annonce les produits à remettre
        JsonNode expected = afterB.get("stops").get(2).get("expectedProducts");
        assertEquals(4, expected.get("SANG").asInt());
        assertEquals(2, expected.get("PCR").asInt());
        assertEquals(1, expected.get("URINE").asInt());

        // Remise au laboratoire : un PCR manque → tournée terminée, écart signalé
        call(token, post("/api/tours/" + tourId + "/stops/" + stopLab + "/complete"),
                "{\"status\":\"FAIT\",\"signedBy\":\"Accueil labo\",\"products\":{\"SANG\":4,\"PCR\":1,\"URINE\":1}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("TERMINEE"));

        JsonNode trace = json(call(token, get("/api/medical/tours/" + tourId + "/traceability"), null)
                .andExpect(status().isOk()));
        JsonNode rec = trace.get("reconciliation");
        assertEquals(7, rec.get("collectedTotal").asInt());
        assertEquals(6, rec.get("deliveredTotal").asInt());
        assertFalse(rec.get("balanced").asBoolean());
        assertEquals(-1, rec.get("gap").get("PCR").asInt());
        List<String> types = eventTypes(trace.get("events"));
        assertEquals(List.of("TOURNEE_DEMARREE", "PRISE_EN_CHARGE", "CORRECTION", "PRISE_EN_CHARGE", "NC_OUVERTE",
                "REMISE", "TOURNEE_TERMINEE", "NC_OUVERTE"), types);
        assertEquals(45.76, trace.get("events").get(1).get("latitude").asDouble());
        JsonNode ncs = trace.get("nonConformities");
        assertEquals(2, ncs.size());
        assertEquals("ECART_TEMPERATURE", ncs.get(0).get("type").asText());
        assertEquals("ECART_QUANTITE", ncs.get(1).get("type").asText());
        assertEquals("CRITIQUE", ncs.get(1).get("severity").asText());
        long gapNc = ncs.get(1).get("id").asLong();

        // Tournée médicale exécutée : conservée pour la traçabilité
        call(token, delete("/api/tours/" + tourId), null).andExpect(status().isConflict());

        // Clôture d'une non-conformité : action corrective obligatoire
        call(token, put("/api/medical/non-conformities/" + gapNc), "{\"status\":\"CLOTUREE\"}")
                .andExpect(status().isBadRequest());
        call(token, put("/api/medical/non-conformities/" + gapNc),
                "{\"status\":\"CLOTUREE\",\"correctiveAction\":\"Écouvillon retrouvé dans le véhicule, remis au labo\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOTUREE"))
                .andExpect(jsonPath("$.closedBy").isNotEmpty());
        call(token, get("/api/medical/non-conformities?status=OUVERTES"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        call(token, get("/api/medical/dashboard"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productsCollected").value(7))
                .andExpect(jsonPath("$.productsDelivered").value(6))
                .andExpect(jsonPath("$.collectedByCategory.SANG").value(4))
                .andExpect(jsonPath("$.temperatureExcursions").value(1))
                .andExpect(jsonPath("$.openNonConformities").value(1))
                .andExpect(jsonPath("$.conformityRate").value(0.0));

        String today = LocalDate.now().toString();
        String csv = call(token, get("/api/medical/custody.csv?from=" + today + "&to=" + today), null)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(csv.contains("empreinte_precedente;empreinte"));
        assertTrue(csv.contains("SANG=4,PCR=1,URINE=1"));

        // Intégrité : chaîne valide, puis falsification d'une ligne → détectée
        call(token, get("/api/medical/custody/verify"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
        CustodyEvent pickup = custodyRepository.findByCompanyIdAndTourIdOrderBySequenceNoAsc(companyOf(tourId), tourId).get(1);
        pickup.setProducts("SANG=30,PCR=2");
        custodyRepository.save(pickup);
        call(token, get("/api/medical/custody/verify"), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.firstInvalidSequence").value(pickup.getSequenceNo()));

        // L'activité médicale ne se quitte pas en laissant un journal orphelin
        call(token, put("/api/company/fleet-profile"), "{\"fleetProfile\":\"MESSAGERIE\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void transitDelayExceeded_opensNonConformity() throws Exception {
        String token = register("TEST-Navette Lente", "lente@bionavette.test", "COLLECTE_SANTE");
        call(token, put("/api/medical/settings"), "{\"maxTransitMinutes\":5}").andExpect(status().isBadRequest());
        call(token, put("/api/medical/settings"), "{\"maxTransitMinutes\":60}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.maxTransitMinutes").value(60));
        long pharma = site(token, "Pharmacie Lointaine", "PHARMACIE", 4.80);
        long lab = site(token, "Laboratoire", "LABORATOIRE", 4.90);
        long tourId = tour(token, "Collecte longue");
        addStop(token, tourId, pharma, "COLLECTE", null);
        JsonNode t = addStop(token, tourId, lab, "LIVRAISON", null);
        long s1 = t.get("stops").get(0).get("id").asLong();
        long s2 = t.get("stops").get(1).get("id").asLong();

        String twoHoursAgo = LocalDateTime.now().minusHours(2).withNano(0).toString();
        call(token, post("/api/tours/" + tourId + "/stops/" + s1 + "/complete"),
                "{\"status\":\"FAIT\",\"products\":{\"SANG\":2},\"occurredAt\":\"" + twoHoursAgo + "\"}")
                .andExpect(status().isOk());
        call(token, post("/api/tours/" + tourId + "/stops/" + s2 + "/complete"),
                "{\"status\":\"FAIT\",\"products\":{\"SANG\":2}}")
                .andExpect(status().isOk());

        JsonNode trace = json(call(token, get("/api/medical/tours/" + tourId + "/traceability"), null));
        assertTrue(trace.get("reconciliation").get("balanced").asBoolean());
        assertTrue(trace.get("reconciliation").get("transits").get(0).get("exceeded").asBoolean());
        assertEquals("DELAI_ACHEMINEMENT", trace.get("nonConformities").get(0).get("type").asText());
    }

    @Test
    void otherActivities_haveNoMedicalModule() throws Exception {
        String token = register("TEST-Messagerie Express", "colis@express.test", "MESSAGERIE");
        call(token, get("/api/medical/dashboard"), null).andExpect(status().isForbidden());
        call(token, get("/api/medical/custody/verify"), null).andExpect(status().isForbidden());

        long client = site(token, "Client", "CLIENT", 4.84);
        long tourId = tour(token, "Tournée colis");
        JsonNode t = addStop(token, tourId, client, "LIVRAISON", null);
        assertFalse(t.get("medical").asBoolean());
        long stopId = t.get("stops").get(0).get("id").asLong();
        call(token, post("/api/tours/" + tourId + "/stops/" + stopId + "/complete"),
                "{\"status\":\"FAIT\",\"parcelCount\":3,\"products\":{\"SANG\":2}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops[0].products").doesNotExist());
        assertEquals(0, custodyRepository.findByCompanyIdAndTourIdOrderBySequenceNoAsc(companyOf(tourId), tourId).size());
        // Une tournée messagerie terminée reste supprimable
        call(token, delete("/api/tours/" + tourId), null).andExpect(status().isOk());
    }

    @Test
    void medicalSettings_defaultTransitLimit() throws Exception {
        String admin = register("TEST-Navette Droits", "droits@bionavette.test", "COLLECTE_SANTE");
        call(admin, get("/api/medical/settings"), null).andExpect(status().isOk())
                .andExpect(jsonPath("$.maxTransitMinutes").value(240));
    }

    private Long companyOf(long tourId) {
        return custodyRepository.findAll().stream().filter(e -> e.getTourId() != null && e.getTourId() == tourId)
                .map(CustodyEvent::getCompanyId).findFirst().orElse(-1L);
    }
}
