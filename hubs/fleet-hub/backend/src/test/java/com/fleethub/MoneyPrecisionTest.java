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

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V27 : les montants sont stockés et calculés en décimal exact. Avec des double,
 * un tarif de 0,10 € + 0,20 €/kg pour 1 kg donnait un devis de 0,30000000000000004 €.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MoneyPrecisionTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void setUp() throws Exception {
        String email = "money-" + System.nanoTime() + "@test.fr";
        MvcResult res = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Money\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        token = objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private JsonNode postJson(String url, String body) throws Exception {
        MvcResult res = mvc.perform(post(url)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is2xxSuccessful())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString());
    }

    @Test
    void quotedCost_fromARate_isExactToTheCent() throws Exception {
        String carrierId = postJson("/api/carriers", "{\"name\":\"Transports Précis\",\"code\":\"TP"
                + (System.nanoTime() % 100000) + "\",\"transportModes\":\"ROAD\"}").get("id").asText();
        String rateId = postJson("/api/shipping-rates", "{\"carrierId\":\"" + carrierId + "\",\"name\":\"Messagerie\","
                + "\"originCountry\":\"FR\",\"destinationCountry\":\"FR\",\"transportMode\":\"ROAD\","
                + "\"baseRate\":0.10,\"ratePerKg\":0.20,\"ratePerCbm\":0}").get("id").asText();

        JsonNode shipment = postJson("/api/shipments", "{\"shipperName\":\"Labo\",\"consigneeName\":\"Plateau\","
                + "\"shippingRateId\":\"" + rateId + "\",\"weightKg\":1,\"volumeM3\":0}");

        assertEquals(0, new BigDecimal("0.30").compareTo(shipment.get("quotedCost").decimalValue()),
                "devis attendu 0.30, obtenu " + shipment.get("quotedCost"));
    }

    @Test
    void purchasePrice_isRoundTrippedExactly() throws Exception {
        JsonNode truck = postJson("/api/trucks", "{\"registration\":\"MP-" + (System.nanoTime() % 1000)
                + "-AB\",\"brand\":\"Renault\",\"model\":\"Master\",\"truckType\":\"VUL\",\"fuelType\":\"DIESEL\","
                + "\"purchasePrice\":32999.99}");
        assertEquals(0, new BigDecimal("32999.99").compareTo(truck.get("purchasePrice").decimalValue()));
    }
}
