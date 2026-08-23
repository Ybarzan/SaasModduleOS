package com.fleethub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleethub.model.Driver;
import com.fleethub.model.FuelRecord;
import com.fleethub.model.TachographDay;
import com.fleethub.model.Truck;
import com.fleethub.repository.DriverRepository;
import com.fleethub.repository.FuelRecordRepository;
import com.fleethub.repository.TachographDayRepository;
import com.fleethub.repository.TruckRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie le canal de push externe (interfaces tachygraphe/GPS/carburant) :
 * authentification par clé API, jointure par clés métier (immatriculation /
 * numéro de permis), persistance tenant-scopée et idempotence.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = "integration.webhook-api-key=test-secret")
class IntegrationIngestTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TruckRepository truckRepository;
    @Autowired
    private DriverRepository driverRepository;
    @Autowired
    private TachographDayRepository tachographDayRepository;
    @Autowired
    private FuelRecordRepository fuelRecordRepository;

    private Map<String, Object> register(String email) throws Exception {
        MvcResult res = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Ingest Co\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"" + email + "\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readValue(res.getResponse().getContentAsString(), Map.class);
    }

    private String token(Map<String, Object> registration) {
        return registration.get("token").toString();
    }

    private long companyId(Map<String, Object> registration) {
        return ((Number) registration.get("companyId")).longValue();
    }

    private void createTruck(String token, String registration) throws Exception {
        mvc.perform(post("/api/trucks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registration\":\"" + registration + "\",\"brand\":\"Renault\",\"model\":\"T520\","
                                + "\"truckType\":\"TRACTEUR\",\"fuelType\":\"DIESEL\","
                                + "\"expectedConsumptionL100Km\":32.0,\"active\":true}"))
                .andExpect(status().isOk());
    }

    private void createDriver(String token, String license) throws Exception {
        mvc.perform(post("/api/drivers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jean\",\"lastName\":\"Dupont\","
                                + "\"licenseNumber\":\"" + license + "\",\"phone\":\"0600000000\","
                                + "\"email\":\"jean@ingest.fr\",\"active\":true}"))
                .andExpect(status().isOk());
    }

    private String payload(String registration, String license, String date) {
        return "{\"positions\":[{\"registration\":\"" + registration
                + "\",\"latitude\":48.8566,\"longitude\":2.3522,\"speedKph\":72.5,"
                + "\"timestamp\":\"2026-08-10T17:00:00\"}],"
                + "\"tachographDays\":[{\"licenseNumber\":\"" + license
                + "\",\"date\":\"" + date + "\",\"drivingHours\":9.5,\"workHours\":12,"
                + "\"restMinutes\":450,\"compliant\":true}],"
                + "\"fuelTransactions\":[{\"registration\":\"" + registration
                + "\",\"date\":\"" + date + "\",\"liters\":120.5,\"amount\":205.0,"
                + "\"odometerKm\":123456.0}]}";
    }

    @Test
    void ingestGpsTachoAndFuel() throws Exception {
        Map<String, Object> registration = register("admin@ingest.fr");
        String token = token(registration);
        long companyId = companyId(registration);
        createTruck(token, "AA-999-ZZ");
        createDriver(token, "L-INGEST-1");
        String date = LocalDate.now().toString();

        // Clé API invalide → 401.
        mvc.perform(post("/api/webhooks/ingest")
                        .header("X-API-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("AA-999-ZZ", "L-INGEST-1", date)))
                .andExpect(status().isUnauthorized());

        // Push valide → tout est persisté.
        mvc.perform(post("/api/webhooks/ingest")
                        .header("X-API-Key", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("AA-999-ZZ", "L-INGEST-1", date)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionsUpdated").value(1))
                .andExpect(jsonPath("$.tachographDaysSaved").value(1))
                .andExpect(jsonPath("$.fuelTransactionsSaved").value(1));

        Truck truck = truckRepository.findByRegistration("AA-999-ZZ").orElseThrow();
        assertEquals(48.8566, truck.getCurrentLatitude(), 0.0001);
        assertEquals(Truck.VehicleStatus.ROULAGE, truck.getCurrentStatus());

        Driver driver = driverRepository.findByLicenseNumber("L-INGEST-1").orElseThrow();
        TachographDay day = tachographDayRepository.findByDriverIdAndDate(driver.getId(), LocalDate.now()).orElseThrow();
        assertEquals(companyId, day.getCompany().getId(),
                "Le jour tachygraphe doit être rattaché à la société de la clé métier");

        FuelRecord fuel = fuelRecordRepository.findByTruckIdAndDate(truck.getId(), LocalDate.now()).stream()
                .findFirst().orElseThrow();
        assertEquals(companyId, fuel.getCompany().getId(),
                "La transaction carburant doit être rattachée à la société de la clé métier");

        // Idempotence : rejouer le push ne duplique pas (upsert tachygraphe,
        // anti-doublon carburant, mise à jour en place GPS).
        mvc.perform(post("/api/webhooks/ingest")
                        .header("X-API-Key", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("AA-999-ZZ", "L-INGEST-1", date)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionsUpdated").value(1))
                .andExpect(jsonPath("$.fuelTransactionsSaved").value(0));

        long dayCount = tachographDayRepository.findByDriverAndDateAfter(driver, LocalDate.now().minusDays(1)).size();
        assertEquals(1, dayCount, "Aucun doublon tachygraphe malgré le replay");
        long fuelCount = fuelRecordRepository.count();
        assertTrue(fuelCount >= 1, "Le plein doit être persisté");
    }

    @Test
    void unknownKeysAreIgnored() throws Exception {
        register("admin2@ingest.fr");
        mvc.perform(post("/api/webhooks/ingest")
                        .header("X-API-Key", "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("ZZ-000-XX", "NO-SUCH-LICENSE", LocalDate.now().toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.positionsUpdated").value(0))
                .andExpect(jsonPath("$.tachographDaysSaved").value(0))
                .andExpect(jsonPath("$.fuelTransactionsSaved").value(0));
    }
}