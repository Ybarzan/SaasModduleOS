package com.fleethub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fleethub.model.*;
import com.fleethub.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Vérifie le volet alertes & notifications : règles par défaut, génération
 * automatique (maintenance à échéance, non-conformité tachygraphe, usage
 * anormal), idempotence du balayage, marquage « lu » et compteur non lus.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private TruckRepository truckRepository;
    @Autowired
    private DriverRepository driverRepository;
    @Autowired
    private MaintenanceRepository maintenanceRepository;
    @Autowired
    private DrivingEventRepository drivingEventRepository;
    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void notificationsGeneratedReadAndIdempotent() throws Exception {
        MvcResult reg = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Alert Co\",\"firstName\":\"A\",\"lastName\":\"B\","
                                + "\"email\":\"admin@alert.fr\",\"password\":\"password123\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode auth = objectMapper.readTree(reg.getResponse().getContentAsString());
        String token = auth.get("token").asText();
        Long companyId = auth.get("companyId").asLong();
        Company company = companyRepository.findById(companyId).orElseThrow();

        // Trucks et drivers.
        MvcResult truckRes = mvc.perform(post("/api/trucks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"registration\":\"AA-123-BB\",\"brand\":\"Renault\",\"model\":\"T520\","
                                + "\"truckType\":\"TRACTEUR\",\"fuelType\":\"DIESEL\","
                                + "\"expectedConsumptionL100Km\":32.0,\"active\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        long truckId = objectMapper.readTree(truckRes.getResponse().getContentAsString()).get("id").asLong();
        Truck t = truckRepository.getReferenceById(truckId);

        MvcResult driverRes = mvc.perform(post("/api/drivers")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"firstName\":\"Jean\",\"lastName\":\"Dupont\","
                                + "\"licenseNumber\":\"L123456\",\"phone\":\"0600000000\","
                                + "\"email\":\"jean@alert.fr\",\"active\":true}"))
                .andExpect(status().isOk())
                .andReturn();
        long driverId = objectMapper.readTree(driverRes.getResponse().getContentAsString()).get("id").asLong();
        Driver d = driverRepository.getReferenceById(driverId);

        // Maintenance planifiée sous 3 jours → alerte MAINTENANCE_ECHEANCE.
        MaintenanceRecord m = new MaintenanceRecord();
        m.setCompany(company);
        m.setTruck(t);
        m.setScheduledDate(LocalDate.now().plusDays(3));
        m.setType(MaintenanceRecord.MaintenanceType.VIDANGE);
        m.setPlanned(true);
        m.setDoneOnTime(false);
        m.setStatus(MaintenanceRecord.MaintenanceStatus.PLANIFIE);
        maintenanceRepository.save(m);

        // Jour tachygraphe non conforme aujourd'hui (11 h de conduite, 15 min de repos)
        // → alerte TACHYGRAPHIE. Le moteur 561/2006 recalculé côté serveur le déclare
        // non conforme, quel que soit le flag envoyé par le client.
        mvc.perform(post("/api/tachograph-days")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"driverId\":" + driverId + ",\"date\":\"" + LocalDate.now()
                                + "\",\"drivingHours\":11,\"workHours\":12,\"restMinutes\":15,"
                                + "\"compliant\":true}"))
                .andExpect(status().isOk());

        // Événement grave → alerte USAGE_ANORMAL.
        DrivingEvent e = new DrivingEvent();
        e.setCompany(company);
        e.setDriver(d);
        e.setTruck(t);
        e.setTimestamp(LocalDateTime.now());
        e.setType(DrivingEvent.EventType.FREINAGE_BRUSQUE);
        e.setSeverity(9);
        e.setSpeedKph(85.0);
        drivingEventRepository.save(e);

        // Les règles par défaut existent.
        mvc.perform(get("/api/notifications/rules").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));

        // Le balayage génère les 3 notifications.
        MvcResult list = mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode notifications = objectMapper.readTree(list.getResponse().getContentAsString());
        long before = notifications.size();
        assertTrue(hasType(notifications, "MAINTENANCE_ECHEANCE"));
        assertTrue(hasType(notifications, "TACHYGRAPHIE_NON_CONFORME"));
        assertTrue(hasType(notifications, "USAGE_ANORMAL"));

        // Un second appel ne duplique pas (idempotence sur 24 h).
        MvcResult again = mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(before, objectMapper.readTree(again.getResponse().getContentAsString()).size());

        // Compteur non lus puis marquage lu.
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(before));
        for (JsonNode n : notifications) {
            mvc.perform(patch("/api/notifications/" + n.get("id").asLong() + "/read")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isNoContent());
        }
        mvc.perform(get("/api/notifications/unread-count").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    private boolean hasType(JsonNode array, String type) {
        for (JsonNode n : array) {
            if (type.equals(n.get("type").asText())) return true;
        }
        return false;
    }
}
