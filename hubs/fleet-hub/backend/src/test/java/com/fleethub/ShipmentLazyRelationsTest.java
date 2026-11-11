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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API du personnel sur les expéditions : les réponses lisent des relations
 * paresseuses (transporteur, tarif, camion) via carrierName / transportMode /
 * truckRegistration. Sans chargement préalable, la sérialisation JSON, faite
 * après la fermeture de la session (open-in-view: false), échouait en 500 —
 * notamment à la création d'une expédition à partir d'un tarif.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ShipmentLazyRelationsTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private String shipmentId;

    private JsonNode call(MockHttpServletRequestBuilder req, String body) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        MvcResult res = mvc.perform(req).andExpect(status().is2xxSuccessful()).andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    @BeforeEach
    void setUp() throws Exception {
        String email = "lazy-" + System.nanoTime() + "@test.fr";
        MvcResult reg = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Lazy\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        token = objectMapper.readTree(reg.getResponse().getContentAsString()).get("token").asText();

        String carrierId = call(post("/api/carriers"), "{\"name\":\"Transports Lazy\",\"code\":\"TL"
                + (System.nanoTime() % 100000) + "\",\"transportModes\":\"ROAD\"}").get("id").asText();
        String rateId = call(post("/api/shipping-rates"), "{\"carrierId\":\"" + carrierId + "\",\"name\":\"Route\","
                + "\"originCountry\":\"FR\",\"destinationCountry\":\"FR\",\"transportMode\":\"ROAD\","
                + "\"baseRate\":50,\"ratePerKg\":0.5,\"ratePerCbm\":0}").get("id").asText();
        String truckId = call(post("/api/trucks"), "{\"registration\":\"LZ-" + (System.nanoTime() % 1000)
                + "-AB\",\"brand\":\"Renault\",\"model\":\"Master\",\"truckType\":\"VUL\",\"fuelType\":\"DIESEL\"}")
                .get("id").asText();

        // Le transporteur est déduit du tarif (proxy paresseux) : c'est ce cas qui échouait.
        JsonNode created = call(post("/api/shipments"), "{\"shipperName\":\"Labo\",\"consigneeName\":\"Plateau\","
                + "\"shippingRateId\":\"" + rateId + "\",\"truckId\":" + truckId + ",\"weightKg\":10,\"volumeM3\":0}");
        assertEquals("Transports Lazy", created.get("carrierName").asText());
        assertEquals("ROAD", created.get("transportMode").asText());
        shipmentId = created.get("id").asText();
    }

    @Test
    void list_detail_andStatusChange_readTheLazyRelations() throws Exception {
        JsonNode page = call(get("/api/shipments"), null);
        JsonNode first = page.has("content") ? page.get("content").get(0) : page.get(0);
        assertEquals("Transports Lazy", first.get("carrierName").asText());

        JsonNode detail = call(get("/api/shipments/" + shipmentId), null);
        assertEquals("Transports Lazy", detail.get("carrierName").asText());
        assertEquals("ROAD", detail.get("transportMode").asText());
        assertEquals(true, detail.get("truckRegistration").asText().startsWith("LZ-"));

        JsonNode updated = call(patch("/api/shipments/" + shipmentId + "/status"), "{\"status\":\"BOOKED\"}");
        assertEquals("Transports Lazy", updated.get("carrierName").asText());
    }
}
