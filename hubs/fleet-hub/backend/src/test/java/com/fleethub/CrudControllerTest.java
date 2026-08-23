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
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CrudControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void login() throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"admin\"}"))
                .andReturn();
        token = objectMapper.readValue(res.getResponse().getContentAsString(), Map.class)
                .get("token").toString();
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mvc.perform(post(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions putJson(String url, String body) throws Exception {
        return mvc.perform(put(url)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private ResultActions getAuth(String url) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + token));
    }

    private ResultActions deleteAuth(String url) throws Exception {
        return mvc.perform(delete(url).header("Authorization", "Bearer " + token));
    }

    private long idFrom(ResultActions actions) throws Exception {
        MvcResult res = actions.andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    // ---------- Chauffeurs ----------

    @Test
    void createDriver_thenAppearsInList_thenDelete() throws Exception {
        long id = idFrom(postJson("/api/drivers",
                "{\"firstName\":\"Unit\",\"lastName\":\"Test\",\"licenseNumber\":\"FR-UNIT-001\","
                        + "\"phone\":\"01 00 00 00 01\",\"email\":\"unit@fleet.fr\",\"hireDate\":\"2026-01-01\",\"active\":true}"));

        MvcResult res = getAuth("/api/drivers").andReturn();
        JsonNode drivers = objectMapper.readTree(res.getResponse().getContentAsString());
        assertTrue(streamToString(drivers).contains("FR-UNIT-001"), "Le chauffeur doit apparaître dans la liste");

        deleteAuth("/api/drivers/" + id).andExpect(status().isOk());
    }

    @Test
    void createDriver_duplicateLicense_returns400() throws Exception {
        postJson("/api/drivers",
                "{\"firstName\":\"Unit\",\"lastName\":\"Test\",\"licenseNumber\":\"FR-UNIT-002\","
                        + "\"phone\":\"01 00 00 00 02\",\"active\":true}")
                .andExpect(status().isOk());
        postJson("/api/drivers",
                "{\"firstName\":\"Unit\",\"lastName\":\"Test\",\"licenseNumber\":\"FR-UNIT-002\","
                        + "\"phone\":\"01 00 00 00 02\",\"active\":true}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void createDriver_missingRequiredFields_returns400() throws Exception {
        postJson("/api/drivers", "{\"firstName\":\"\",\"lastName\":\"\",\"phone\":\"\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void updateDriver_modifiesFields() throws Exception {
        long id = idFrom(postJson("/api/drivers",
                "{\"firstName\":\"Unit\",\"lastName\":\"Test\",\"licenseNumber\":\"FR-UNIT-003\","
                        + "\"phone\":\"01 00 00 00 03\",\"active\":true}"));

        putJson("/api/drivers/" + id,
                "{\"firstName\":\"Unit\",\"lastName\":\"Renamed\",\"licenseNumber\":\"FR-UNIT-003\","
                        + "\"phone\":\"06 06 06 06 06\",\"active\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value("Renamed"))
                .andExpect(jsonPath("$.phone").value("06 06 06 06 06"));

        deleteAuth("/api/drivers/" + id).andExpect(status().isOk());
    }

    @Test
    void updateDriver_notFound_returns404() throws Exception {
        putJson("/api/drivers/999999",
                "{\"firstName\":\"Unit\",\"lastName\":\"Test\",\"licenseNumber\":\"FR-UNIT-004\","
                        + "\"phone\":\"01 00 00 00 04\",\"active\":true}")
                .andExpect(status().isNotFound());
    }

    // ---------- Camions ----------

    @Test
    void createTruck_thenUpdate_thenDelete() throws Exception {
        String body = "{\"registration\":\"GT-UNIT-01\",\"brand\":\"Unit\",\"model\":\"Truck\","
                + "\"modelYear\":2025,\"truckType\":\"TRACTEUR\",\"fuelType\":\"DIESEL\","
                + "\"capacityTons\":26,\"expectedConsumptionL100Km\":32,\"active\":true}";
        long id = idFrom(postJson("/api/trucks", body).andExpect(status().isOk()));

        putJson("/api/trucks/" + id,
                "{\"registration\":\"GT-UNIT-01\",\"brand\":\"Unit\",\"model\":\"Truck\","
                        + "\"modelYear\":2026,\"truckType\":\"TRACTEUR\",\"fuelType\":\"DIESEL\","
                        + "\"capacityTons\":26,\"expectedConsumptionL100Km\":31,\"active\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2026))
                .andExpect(jsonPath("$.expectedConsumptionL100Km").value(31.0));

        deleteAuth("/api/trucks/" + id).andExpect(status().isOk());
    }

    @Test
    void createTruck_invalidEnum_returns400() throws Exception {
        postJson("/api/trucks",
                "{\"registration\":\"GT-UNIT-02\",\"brand\":\"Unit\",\"model\":\"Truck\","
                        + "\"truckType\":\"VTT\",\"fuelType\":\"DIESEL\",\"expectedConsumptionL100Km\":32,\"active\":true}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void createTruck_duplicateRegistration_returns400() throws Exception {
        String body = "{\"registration\":\"GT-UNIT-03\",\"brand\":\"Unit\",\"model\":\"Truck\","
                + "\"truckType\":\"TRACTEUR\",\"fuelType\":\"DIESEL\",\"expectedConsumptionL100Km\":32,\"active\":true}";
        long id = idFrom(postJson("/api/trucks", body).andExpect(status().isOk()));
        postJson("/api/trucks", body).andExpect(status().isBadRequest());
        deleteAuth("/api/trucks/" + id).andExpect(status().isOk());
    }

    // ---------- Affectations ----------

    @Test
    void createAssignment_releasesConflictingActiveOne() throws Exception {
        long truckId = idFrom(postJson("/api/trucks",
                "{\"registration\":\"GT-UNIT-04\",\"brand\":\"Unit\",\"model\":\"Truck\","
                        + "\"truckType\":\"TRACTEUR\",\"fuelType\":\"DIESEL\",\"expectedConsumptionL100Km\":32,\"active\":true}"));
        long d1 = idFrom(postJson("/api/drivers",
                "{\"firstName\":\"A\",\"lastName\":\"Un\",\"licenseNumber\":\"FR-UNIT-101\",\"phone\":\"1\",\"active\":true}"));
        long d2 = idFrom(postJson("/api/drivers",
                "{\"firstName\":\"B\",\"lastName\":\"Deux\",\"licenseNumber\":\"FR-UNIT-102\",\"phone\":\"2\",\"active\":true}"));

        long a1 = idFrom(postJson("/api/assignments",
                "{\"driverId\":" + d1 + ",\"truckId\":" + truckId + ",\"startDate\":\"2026-01-01\",\"endDate\":null,\"active\":true}"));
        long a2 = idFrom(postJson("/api/assignments",
                "{\"driverId\":" + d2 + ",\"truckId\":" + truckId + ",\"startDate\":\"2026-02-01\",\"endDate\":null,\"active\":true}"));

        MvcResult res = getAuth("/api/assignments").andReturn();
        JsonNode all = objectMapper.readTree(res.getResponse().getContentAsString());
        long activeForTruck = 0;
        long inactiveForTruck = 0;
        for (JsonNode a : all) {
            if (a.get("truckId").asLong() == truckId) {
                if (a.get("active").asBoolean()) activeForTruck++;
                else inactiveForTruck++;
            }
        }
        assertEquals(1, activeForTruck, "Une seule affectation active par camion");
        assertEquals(1, inactiveForTruck, "La première affectation doit être désactivée");

        deleteAuth("/api/assignments/" + a2).andExpect(status().isOk());
        deleteAuth("/api/assignments/" + a1).andExpect(status().isOk());
        deleteAuth("/api/drivers/" + d2).andExpect(status().isOk());
        deleteAuth("/api/drivers/" + d1).andExpect(status().isOk());
        deleteAuth("/api/trucks/" + truckId).andExpect(status().isOk());
    }

    // ---------- Trajets / carburant / tachygraphe ----------

    @Test
    void createTrip_list_put_delete() throws Exception {
        String body = "{\"driverId\":1,\"truckId\":1,\"startTime\":\"2026-08-01T08:00:00\","
                + "\"endTime\":\"2026-08-01T14:00:00\",\"distanceKm\":350,\"cargoWeightTons\":20,"
                + "\"loaded\":true,\"status\":\"TERMINE\",\"onTime\":true}";
        long id = idFrom(postJson("/api/trips", body).andExpect(status().isOk())
                .andExpect(jsonPath("$.driverName").isNotEmpty())
                .andExpect(jsonPath("$.truckRegistration").value("GT-123-AB")));

        putJson("/api/trips/" + id,
                "{\"driverId\":1,\"truckId\":1,\"startTime\":\"2026-08-01T08:00:00\","
                        + "\"endTime\":\"2026-08-01T14:00:00\",\"distanceKm\":400,\"cargoWeightTons\":20,"
                        + "\"loaded\":true,\"status\":\"TERMINE\",\"onTime\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distanceKm").value(400.0));

        deleteAuth("/api/trips/" + id).andExpect(status().isOk());
    }

    @Test
    void createTrip_unknownDriver_returns404() throws Exception {
        postJson("/api/trips",
                "{\"driverId\":999999,\"truckId\":1,\"startTime\":\"2026-08-01T08:00:00\","
                        + "\"endTime\":\"2026-08-01T14:00:00\",\"distanceKm\":350,\"status\":\"TERMINE\",\"onTime\":true}")
                .andExpect(status().isNotFound());
    }

    @Test
    void createFuelRecord_roundTrip() throws Exception {
        long id = idFrom(postJson("/api/fuel-records",
                "{\"truckId\":1,\"date\":\"2026-08-05\",\"liters\":150.5,\"amount\":245.3,\"odometerKm\":120000}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truckRegistration").value("GT-123-AB")));

        getAuth("/api/fuel-records").andExpect(status().isOk());
        getAuth("/api/fuel-records?truckId=1&from=2026-07-01T00:00:00&to=2026-08-31T23:59:59")
                .andExpect(status().isOk());

        deleteAuth("/api/fuel-records/" + id).andExpect(status().isOk());
    }

    @Test
    void createTachographDay_roundTrip() throws Exception {
        long id = idFrom(postJson("/api/tachograph-days",
                "{\"driverId\":1,\"date\":\"2026-08-05\",\"drivingHours\":8.5,\"workHours\":10,\"restMinutes\":45,\"compliant\":true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.driverName").isNotEmpty()));

        putJson("/api/tachograph-days/" + id,
                "{\"driverId\":1,\"date\":\"2026-08-05\",\"drivingHours\":8.5,\"workHours\":10,\"restMinutes\":30,\"compliant\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compliant").value(false));

        deleteAuth("/api/tachograph-days/" + id).andExpect(status().isOk());
    }

    @Test
    void listTachographDays_withDateTimeRange_isAccepted() throws Exception {
        // Le frontend envoie from/to en ISO datetime (comme pour /trips et /driving-events),
        // pas en simple LocalDate : la liste doit rester filtrable sans erreur 401/500.
        getAuth("/api/tachograph-days?driverId=1&from=2026-07-01T00:00:00&to=2026-08-31T23:59:59")
                .andExpect(status().isOk());
    }

    @Test
    void writeWithoutToken_isRejected() throws Exception {
        mvc.perform(post("/api/drivers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"A\"}"))
                .andExpect(status().is4xxClientError());
    }

    private String streamToString(JsonNode arr) {
        StringBuilder sb = new StringBuilder();
        arr.forEach(n -> sb.append(n.toString()).append(';'));
        return sb.toString();
    }
}
