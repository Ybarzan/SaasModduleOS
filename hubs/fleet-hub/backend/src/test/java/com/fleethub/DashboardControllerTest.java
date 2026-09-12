package com.fleethub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleethub.model.Truck;
import com.fleethub.repository.TruckRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/dashboard/summary : première page vue après connexion, jusqu'ici sans
 * aucun test (ni sur le service, ni sur le contrôleur). Chaque test enregistre
 * sa propre société fraîche : le résumé est mis en cache par société+période
 * (tenantKeyGenerator), donc un seul appel à /summary par test évite toute
 * lecture d'un résultat mis en cache avant que les données ne soient créées.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DashboardControllerTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TruckRepository truckRepository;

    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        String email = "dashboard-" + System.nanoTime() + "@test.fr";
        MvcResult res = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Dashboard Test\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        adminToken = objectMapper.readTree(res.getResponse().getContentAsString()).get("token").asText();
    }

    private long createTruck(String registration, boolean active) throws Exception {
        MvcResult res = mvc.perform(post("/api/trucks")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registration\":\"" + registration + "\",\"brand\":\"Unit\",\"model\":\"Truck\","
                                + "\"truckType\":\"TRACTEUR\",\"fuelType\":\"DIESEL\",\"expectedConsumptionL100Km\":32,"
                                + "\"active\":" + active + "}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private void setStatus(long truckId, Truck.VehicleStatus status) {
        Truck t = truckRepository.findById(truckId).orElseThrow();
        t.setCurrentStatus(status);
        truckRepository.save(t);
    }

    private long createDriver(String license) throws Exception {
        MvcResult res = mvc.perform(post("/api/drivers")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jean\",\"lastName\":\"Test\",\"licenseNumber\":\"" + license + "\","
                                + "\"phone\":\"01 00 00 00 00\",\"active\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void summary_withoutAuthentication_isRejected() throws Exception {
        mvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void summary_chauffeurRole_isForbidden() throws Exception {
        long driverId = createDriver("FR-DASH-" + System.nanoTime());
        mvc.perform(post("/api/drivers/" + driverId + "/pin")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isNoContent());
        MvcResult loginRes = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"chauffeur-" + driverId + "@pointage.internal\",\"password\":\"1234\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String chauffeurToken = objectMapper.readTree(loginRes.getResponse().getContentAsString()).get("token").asText();

        mvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + chauffeurToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void summary_freshCompany_returnsZeroedDefaults() throws Exception {
        mvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fleetSize").value(0))
                .andExpect(jsonPath("$.activeCouples").value(0))
                .andExpect(jsonPath("$.totalKm").value(0))
                .andExpect(jsonPath("$.alertsCount").value(0))
                .andExpect(jsonPath("$.nonCompliantDrivingDays").value(0))
                .andExpect(jsonPath("$.vehiclesInService").value(0))
                .andExpect(jsonPath("$.vehiclesStopped").value(0))
                .andExpect(jsonPath("$.vehiclesAlerted").value(0))
                .andExpect(jsonPath("$.topCouples").isEmpty());
    }

    @Test
    void summary_countsOnlyActiveTrucksInFleetSize() throws Exception {
        createTruck("GT-DASH-ACTIVE", true);
        createTruck("GT-DASH-INACTIVE", false);

        mvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fleetSize").value(1));
    }

    @Test
    void summary_bucketsTrucksByVehicleStatus() throws Exception {
        setStatus(createTruck("GT-DASH-ROULAGE", true), Truck.VehicleStatus.ROULAGE);
        setStatus(createTruck("GT-DASH-ARRET", true), Truck.VehicleStatus.ARRET);
        setStatus(createTruck("GT-DASH-REPOS", true), Truck.VehicleStatus.REPOS);
        setStatus(createTruck("GT-DASH-ALERTE", true), Truck.VehicleStatus.ALERTE);
        setStatus(createTruck("GT-DASH-IMMOBILISE", true), Truck.VehicleStatus.IMMOBILISE);

        mvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fleetSize").value(5))
                .andExpect(jsonPath("$.vehiclesInService").value(1))
                .andExpect(jsonPath("$.vehiclesStopped").value(2))
                .andExpect(jsonPath("$.vehiclesAlerted").value(2));
    }

    @Test
    void summary_truckWithNoStatusYet_isNotCountedInAnyBucket() throws Exception {
        // Un camion tout juste créé n'a pas encore de currentStatus (pas de GPS/donnée
        // temps réel) : il doit compter dans fleetSize sans faire planter ni fausser
        // les compteurs en service/à l'arrêt/en alerte.
        createTruck("GT-DASH-NOSTATUS", true);

        mvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fleetSize").value(1))
                .andExpect(jsonPath("$.vehiclesInService").value(0))
                .andExpect(jsonPath("$.vehiclesStopped").value(0))
                .andExpect(jsonPath("$.vehiclesAlerted").value(0));
    }

    @Test
    void summary_countsNonCompliantTachographDaysInCurrentMonth() throws Exception {
        long driverId = createDriver("FR-DASH-TACHO-" + System.nanoTime());
        // 11h de conduite + 15 min de repos : dépasse la limite de 10h et le repos
        // minimal de 45 min (voir TachographServiceTest) -> non conforme.
        String today = java.time.LocalDate.now().toString();
        mvc.perform(post("/api/tachograph-days")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":" + driverId + ",\"date\":\"" + today + "\","
                                + "\"drivingHours\":11.0,\"workHours\":12,\"restMinutes\":15,\"compliant\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.compliant").value(false));

        mvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nonCompliantDrivingDays").value(1));
    }

    @Test
    void summary_defaultPeriod_isAcceptedWithoutParam() throws Exception {
        mvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void summary_tenantIsolation_doesNotSeeOtherCompanyTrucks() throws Exception {
        createTruck("GT-DASH-MINE", true);

        String otherEmail = "dashboard-other-" + System.nanoTime() + "@test.fr";
        MvcResult otherRes = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Dashboard Other\",\"firstName\":\"C\",\"lastName\":\"D\","
                                + "\"email\":\"" + otherEmail + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode otherBody = objectMapper.readTree(otherRes.getResponse().getContentAsString());
        String otherToken = otherBody.get("token").asText();

        mvc.perform(get("/api/dashboard/summary").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fleetSize").value(0));
    }
}
