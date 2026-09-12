package com.fleethub.service;

import com.fleethub.dto.PointageStatusDto;
import com.fleethub.dto.PointageSummaryDto;
import com.fleethub.model.Company;
import com.fleethub.model.Driver;
import com.fleethub.model.PointageEvent;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.repository.DriverRepository;
import com.fleethub.repository.PointageEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Calcul de conformité 561/2006 (limite de 4h30 de conduite continue, art. 7)
 * du pointage chauffeur. Les horodatages des événements sont insérés
 * directement en base (pas via {@link PointageService#start}/{@code pause}/
 * {@code resume}, qui datent toujours "maintenant") afin de pouvoir tester
 * des services de plusieurs heures sans attendre en temps réel.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PointageServiceTest {

    @Autowired
    private PointageService pointageService;
    @Autowired
    private CompanyRepository companyRepository;
    @Autowired
    private DriverRepository driverRepository;
    @Autowired
    private PointageEventRepository eventRepository;

    private Long companyId;
    private Driver driver;

    @BeforeEach
    void setUp() {
        Company company = Company.builder()
                .name("Pointage Service Test")
                .plan(Company.SubscriptionPlan.TRIAL)
                .status(Company.CompanyStatus.ACTIVE)
                .accessCode(Company.generateAccessCode())
                .createdAt(LocalDateTime.now())
                .build();
        company = companyRepository.save(company);
        companyId = company.getId();

        driver = new Driver();
        driver.setCompany(company);
        driver.setFirstName("Jean");
        driver.setLastName("Test");
        driver.setLicenseNumber("FR-SVC-" + System.nanoTime());
        driver.setPhone("00 00 00 00 00");
        driver.setActive(true);
        driver = driverRepository.save(driver);
    }

    private void event(PointageEvent.Type type, LocalDateTime occurredAt) {
        PointageEvent e = new PointageEvent();
        e.setCompany(driver.getCompany());
        e.setDriver(driver);
        e.setType(type);
        e.setOccurredAt(occurredAt);
        e.setCreatedAt(occurredAt);
        eventRepository.save(e);
    }

    @Test
    void shortShift_isCompliant() {
        event(PointageEvent.Type.DEBUT, LocalDateTime.now().minusHours(2));

        PointageSummaryDto summary = pointageService.end(companyId, driver.getId());

        assertTrue(summary.compliant(), "2h de conduite continue doit être conforme");
        assertTrue(summary.totalDrivingSeconds() >= 2 * 3600 - 5 && summary.totalDrivingSeconds() <= 2 * 3600 + 30,
                "La durée de conduite doit correspondre à l'écart réel (~2h)");
        assertEquals(0, summary.pauseCount());
    }

    @Test
    void continuousDrivingOver4h30_isNonCompliant() {
        event(PointageEvent.Type.DEBUT, LocalDateTime.now().minusHours(5));

        PointageSummaryDto summary = pointageService.end(companyId, driver.getId());

        assertFalse(summary.compliant(), "5h de conduite continue dépasse la limite de 4h30 (art. 7, 561/2006)");
    }

    @Test
    void pauseResetsContinuousDrivingCounter_staysCompliant() {
        LocalDateTime start = LocalDateTime.now().minusHours(3);
        event(PointageEvent.Type.DEBUT, start);
        event(PointageEvent.Type.PAUSE_DEBUT, start.plusHours(2)); // 1er segment : 2h, conforme
        event(PointageEvent.Type.PAUSE_FIN, start.plusHours(2).plusMinutes(45)); // pause de 45 min

        PointageSummaryDto summary = pointageService.end(companyId, driver.getId());

        assertTrue(summary.compliant(), "Deux segments courts séparés par une pause restent conformes");
        assertEquals(1, summary.pauseCount());
        assertTrue(summary.totalPauseSeconds() >= 45 * 60 - 5 && summary.totalPauseSeconds() <= 45 * 60 + 30,
                "La pause déclarée doit être comptabilisée (~45 min)");
    }

    @Test
    void longFirstSegment_makesShiftNonCompliant_evenIfSecondSegmentIsShort() {
        LocalDateTime start = LocalDateTime.now().minusHours(6);
        event(PointageEvent.Type.DEBUT, start);
        event(PointageEvent.Type.PAUSE_DEBUT, start.plusHours(5)); // 1er segment : 5h > 4h30
        event(PointageEvent.Type.PAUSE_FIN, start.plusHours(5).plusMinutes(30));

        PointageSummaryDto summary = pointageService.end(companyId, driver.getId());

        assertFalse(summary.compliant(),
                "Un seul segment hors limite suffit à rendre toute la journée non conforme, même si le second segment est court");
        assertEquals(1, summary.pauseCount());
    }

    @Test
    void status_whilePaused_reportsPauseStateAndPriorDrivingTime() {
        LocalDateTime start = LocalDateTime.now().minusMinutes(90);
        event(PointageEvent.Type.DEBUT, start);
        event(PointageEvent.Type.PAUSE_DEBUT, start.plusMinutes(60)); // 1h de conduite avant la pause

        PointageStatusDto status = pointageService.status(companyId, driver.getId());

        assertEquals("PAUSE", status.state());
        assertTrue(status.pauseSeconds() >= 30 * 60 - 5, "La pause en cours doit être comptée depuis PAUSE_DEBUT (~30 min)");
        assertTrue(status.continuousDrivingSeconds() >= 60 * 60 - 5 && status.continuousDrivingSeconds() <= 60 * 60 + 30,
                "La conduite affichée doit rester celle du segment qui a précédé la pause (~1h), pas continuer à courir pendant la pause");
    }

    @Test
    void openShift_ignoresEventsBeforePreviousFin() {
        LocalDateTime oldStart = LocalDateTime.now().minusHours(10);
        event(PointageEvent.Type.DEBUT, oldStart);
        event(PointageEvent.Type.FIN, oldStart.plusHours(8)); // vieux service déjà terminé, hors limite

        PointageStatusDto status = pointageService.status(companyId, driver.getId());
        assertEquals("IDLE", status.state(), "Un service déjà terminé ne doit pas laisser le chauffeur en cours");

        pointageService.start(companyId, driver.getId());
        PointageStatusDto fresh = pointageService.status(companyId, driver.getId());

        assertEquals("ACTIVE", fresh.state());
        assertTrue(fresh.continuousDrivingSeconds() < 5,
                "Le nouveau service ne doit pas hériter de la conduite de l'ancien service déjà clôturé");
    }

    @Test
    void start_conflictsWhenShiftAlreadyOpen() {
        pointageService.start(companyId, driver.getId());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> pointageService.start(companyId, driver.getId()));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void pause_withoutOpenShift_conflicts() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> pointageService.pause(companyId, driver.getId()));
        assertEquals(409, ex.getStatusCode().value());
    }

    @Test
    void resume_withoutOpenPause_conflicts() {
        pointageService.start(companyId, driver.getId());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> pointageService.resume(companyId, driver.getId()));
        assertEquals(409, ex.getStatusCode().value());
    }
}
