package com.fleethub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Planification : import CSV des commandes, répartition automatique entre
 * véhicules (solveur intégré : pas de VROOM ni de géocodage réseau en test).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {"app.geocoding.enabled=false", "app.routing.vroom-url="})
class OrderDispatchTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private final String date = LocalDate.now().plusDays(1).toString();

    private ResultActions call(MockHttpServletRequestBuilder req, String body) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(req);
    }

    private JsonNode json(ResultActions r) throws Exception {
        return objectMapper.readTree(r.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private long id(ResultActions r) throws Exception {
        return json(r.andExpect(status().isOk())).get("id").asLong();
    }

    private JsonNode importCsv(String csv) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "commandes.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
        return json(mvc.perform(multipart("/api/orders/import").file(file).param("date", date)
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk()));
    }

    @BeforeEach
    void setUp() throws Exception {
        token = json(mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"companyName\":\"TEST-Messagerie Rhône\",\"firstName\":\"A\",\"lastName\":\"B\",\"email\":\"od-"
                        + System.nanoTime() + "@test.fr\",\"password\":\"password123\",\"fleetProfile\":\"MESSAGERIE\"}"))
                .andExpect(status().isCreated())).get("token").asText();
    }

    private static final String CSV = "﻿Référence;Nom;Adresse;Code postal;Ville;Latitude;Longitude;Type;Début;Fin;Durée;Quantité;Notes\n"
            + "C-001;Boulangerie Martin;1 rue A;69001;Lyon;45.7676;4.8344;livraison;08:00;12:00;5;4;Sonner 2 fois\n"
            + "C-002;Garage Durand;2 rue B;69003;Lyon;45.7600;4.8590;livraison;;;5;4;\n"
            + "C-003;Fleuriste Rose;3 rue C;69006;Lyon;45.7700;4.8500;enlèvement;9h;11h30;10;4;\n"
            + "C-004;Cabinet Dr Petit;4 rue D;69007;Lyon;45.7480;4.8420;livraison;;;5;4;\n"
            + "C-005;Librairie;5 rue E;69002;Lyon;45.7580;4.8320;livraison;;;5;4;\n"
            + "C-006;\"Pharmacie \"\"Centrale\"\"\";6 rue F;69002;Lyon;45.7550;4.8330;livraison;;;5;4;\n"
            + "C-007;Boulangerie Martin;1 rue A;69001;Lyon;45.7676;4.8344;livraison;14:00;16:00;5;2;Second passage\n"
            + "C-008;Sans GPS;10 rue X;69009;Lyon;;;livraison;;;5;1;\n"
            + "C-009;Erreur heure;;69001;Lyon;45.76;4.83;livraison;25:99;;5;1;\n"
            + "C-010;Erreur type;;69001;Lyon;45.76;4.83;parachutage;;;5;1;\n";

    @Test
    void importThenDispatch_acrossTwoVans() throws Exception {
        JsonNode imp = importCsv(CSV);
        assertEquals(10, imp.get("rowsRead").asInt());
        assertEquals(8, imp.get("ordersCreated").asInt(), imp.toString());
        assertEquals(7, imp.get("sitesCreated").asInt(), "Boulangerie Martin est rapprochée, pas recréée");
        assertEquals(1, imp.get("ordersWithoutLocation").asInt());
        assertEquals(2, imp.get("errors").size());
        assertTrue(imp.get("errors").get(0).asText().startsWith("Ligne 10"));

        call(get("/api/orders?date=" + date), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(8))
                .andExpect(jsonPath("$[5].siteName").value("Pharmacie \"Centrale\""))
                .andExpect(jsonPath("$[2].windowStart").value("09:00:00"))
                .andExpect(jsonPath("$[2].type").value("ENLEVEMENT"));

        long depot = id(call(post("/api/sites"), "{\"name\":\"Dépôt Vénissieux\",\"kind\":\"DEPOT\",\"latitude\":45.705,\"longitude\":4.880}"));
        long van1 = id(call(post("/api/trucks"), "{\"registration\":\"VU-101\",\"brand\":\"Renault\",\"model\":\"Master\",\"truckType\":\"VUL\",\"fuelType\":\"DIESEL\",\"active\":true}"));
        long van2 = id(call(post("/api/trucks"), "{\"registration\":\"VU-102\",\"brand\":\"Renault\",\"model\":\"Master\",\"truckType\":\"VUL\",\"fuelType\":\"DIESEL\",\"active\":true}"));

        JsonNode res = json(call(post("/api/orders/dispatch"), "{\"date\":\"" + date + "\",\"depotId\":" + depot
                + ",\"start\":\"07:30\",\"end\":\"17:00\",\"namePrefix\":\"Lyon\",\"vehicles\":["
                + "{\"truckId\":" + van1 + ",\"capacity\":16},{\"truckId\":" + van2 + ",\"capacity\":16}]}")
                .andExpect(status().isOk()));

        assertEquals("intégré", res.get("solver").asText());
        assertEquals(2, res.get("tours").size(), "26 unités pour 2 × 16 : les deux véhicules sont nécessaires");
        int stops = 0;
        for (JsonNode t : res.get("tours")) {
            stops += t.get("stopsTotal").asInt();
            assertTrue(t.get("name").asText().startsWith("Lyon "));
            assertTrue(t.get("plannedDistanceKm").asDouble() > 0);
            assertEquals(0, t.get("stopsLate").asInt(), "aucun arrêt hors créneau : " + t);
        }
        assertEquals(7, stops);
        assertEquals(1, res.get("unassigned").size());
        assertTrue(res.get("unassigned").get(0).get("reason").asText().startsWith("Site non localisé"));

        // Les commandes placées sont « planifiées » et pointent vers leur tournée
        JsonNode orders = json(call(get("/api/orders?date=" + date), null));
        long planned = 0;
        for (JsonNode o : orders) if ("PLANIFIEE".equals(o.get("status").asText())) planned++;
        assertEquals(7, planned);

        // Une nouvelle répartition ne reprend pas les commandes déjà planifiées
        call(post("/api/orders/dispatch"), "{\"date\":\"" + date + "\",\"depotId\":" + depot
                + ",\"vehicles\":[{\"truckId\":" + van1 + "}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tours.length()").value(0))
                .andExpect(jsonPath("$.unassigned.length()").value(1));

        // Supprimer une tournée remet ses commandes « à planifier »
        long tourId = res.get("tours").get(0).get("id").asLong();
        int freed = res.get("tours").get(0).get("stopsTotal").asInt();
        call(delete("/api/tours/" + tourId), null).andExpect(status().isOk());
        JsonNode after = json(call(get("/api/orders?date=" + date), null));
        long toPlan = 0;
        for (JsonNode o : after) if ("A_PLANIFIER".equals(o.get("status").asText())) toPlan++;
        assertEquals(freed + 1, toPlan);
    }

    @Test
    void dispatch_reportsCapacityShortfall() throws Exception {
        importCsv(CSV);
        long depot = id(call(post("/api/sites"), "{\"name\":\"Dépôt\",\"kind\":\"DEPOT\",\"latitude\":45.705,\"longitude\":4.880}"));
        long drv = id(call(post("/api/drivers"), "{\"firstName\":\"Nora\",\"lastName\":\"Belkacem\",\"licenseNumber\":\"FR-OD-"
                + System.nanoTime() + "\",\"phone\":\"0600000000\",\"active\":true}"));

        JsonNode res = json(call(post("/api/orders/dispatch"), "{\"date\":\"" + date + "\",\"depotId\":" + depot
                + ",\"vehicles\":[{\"driverId\":" + drv + ",\"capacity\":10}]}").andExpect(status().isOk()));
        assertEquals(1, res.get("tours").size());
        assertTrue(res.get("tours").get(0).get("name").asText().endsWith("Nora B."));
        assertEquals(drv, res.get("tours").get(0).get("driverId").asLong());
        // 7 commandes localisées, capacité 10 : au moins 4 commandes restent à placer (+1 sans GPS)
        assertTrue(res.get("unassigned").size() >= 5, res.get("unassigned").toString());
    }

    @Test
    void dispatch_validation() throws Exception {
        long noGps = id(call(post("/api/sites"), "{\"name\":\"Dépôt sans GPS\",\"kind\":\"DEPOT\"}"));
        call(post("/api/orders/dispatch"), "{\"date\":\"" + date + "\",\"depotId\":" + noGps + ",\"vehicles\":[{\"capacity\":5}]}")
                .andExpect(status().isBadRequest());
        call(post("/api/orders/dispatch"), "{\"date\":\"" + date + "\",\"depotId\":" + noGps + ",\"vehicles\":[]}")
                .andExpect(status().isBadRequest());
        MockMultipartFile bad = new MockMultipartFile("file", "x.csv", "text/csv", "foo;bar\n1;2\n".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/orders/import").file(bad).param("date", date).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
