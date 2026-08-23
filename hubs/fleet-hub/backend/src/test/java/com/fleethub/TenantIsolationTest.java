package com.fleethub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie l'isolation des données entre sociétés (multi-tenant) et les
 * accès du back-office plateforme (SAAS_ADMIN).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TenantIsolationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String register(String company, String email) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"" + company + "\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        assertEquals("TRIAL", body.get("plan").asText(), "Une nouvelle société démarre en essai");
        assertTrue(body.get("companyId").asLong() > 0, "Le tenant doit être créé");
        return body.get("token").asText();
    }

    private String login(String username, String password) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andReturn();
        return objectMapper.readValue(res.getResponse().getContentAsString(), Map.class)
                .get("token").toString();
    }

    private long createDriver(String token, String license) throws Exception {
        MvcResult res = mvc.perform(post("/api/drivers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Tenant\",\"lastName\":\"Test\",\"licenseNumber\":\""
                                + license + "\",\"phone\":\"00 00 00 00 00\",\"active\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void dataIsolation_betweenTwoCompanies() throws Exception {
        String tokenA = register("Société Alpha", "alpha@test.fr");
        String tokenB = register("Société Bêta", "beta@test.fr");

        long driverA = createDriver(tokenA, "FR-TENANT-001");
        long driverB = createDriver(tokenB, "FR-TENANT-002");

        // Chaque société ne voit que ses propres chauffeurs.
        MvcResult listA = mvc.perform(get("/api/drivers")
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk()).andReturn();
        String bodyA = listA.getResponse().getContentAsString();
        assertTrue(bodyA.contains("FR-TENANT-001"), "La société A doit voir son chauffeur");
        assertTrue(!bodyA.contains("FR-TENANT-002"), "La société A ne doit pas voir le chauffeur de B");

        MvcResult listB = mvc.perform(get("/api/drivers")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk()).andReturn();
        assertTrue(listB.getResponse().getContentAsString().contains("FR-TENANT-002"));

        // Accès direct à l'ID d'un chauffeur d'une autre société -> 404.
        mvc.perform(get("/api/drivers/" + driverB)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/drivers/" + driverA)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound());
    }

    @Test
    void planLimit_blocksExtraVehicles() throws Exception {
        String token = register("Société Gamma", "gamma@test.fr");
        String base = "{\"registration\":\"%s\",\"brand\":\"Unit\",\"model\":\"Truck\","
                + "\"truckType\":\"TRACTEUR\",\"fuelType\":\"DIESEL\","
                + "\"expectedConsumptionL100Km\":32,\"active\":true}";

        for (int i = 1; i <= 10; i++) { // TRIAL = 10 véhicules max
            mvc.perform(post("/api/trucks")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(String.format(base, "GT-TENANT-" + String.format("%03d", i))))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/trucks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(String.format(base, "GT-TENANT-011")))
                .andExpect(status().isForbidden());
    }

    @Test
    void backoffice_requiresSaasAdminRole() throws Exception {
        String tenantToken = login("admin", "admin");
        String saasToken = login("saasadmin", "admin");

        // Un tenant ne peut pas accéder au back-office plateforme.
        mvc.perform(get("/api/admin/companies")
                        .header("Authorization", "Bearer " + tenantToken))
                .andExpect(status().isForbidden());

        // L'opérateur plateforme y accède.
        mvc.perform(get("/api/admin/companies")
                        .header("Authorization", "Bearer " + saasToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").exists());
    }
}
