package com.fleethub.service;

import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.dto.PointageRosterDto;
import com.fleethub.dto.PointageStatusDto;
import com.fleethub.dto.PointageSummaryDto;
import com.fleethub.model.AppUser;
import com.fleethub.model.Company;
import com.fleethub.model.Driver;
import com.fleethub.model.PointageEvent;
import com.fleethub.model.PointageEvent.Type;
import com.fleethub.repository.AppUserRepository;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.repository.DriverRepository;
import com.fleethub.repository.PointageEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Pointage chauffeur : début / pause / reprise / fin de service, déclarés par
 * le chauffeur lui-même via le portail dédié (rôle CHAUFFEUR). Vient en
 * complément de la tachygraphie (relevé a posteriori) avec une alerte de
 * pause en temps réel, ce que la tachygraphie seule ne permet pas.
 */
@Service
@RequiredArgsConstructor
public class PointageService {

    /** Art. 7, règlement (CE) 561/2006 : 4h30 de conduite continue max. */
    private static final long CONTINUOUS_DRIVING_LIMIT_SECONDS = 4 * 3600L + 30 * 60L;

    private final PointageEventRepository eventRepository;
    private final DriverRepository driverRepository;
    private final CompanyRepository companyRepository;
    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;

    private static String loginUsername(Long driverId) {
        return "chauffeur-" + driverId + "@pointage.internal";
    }

    @Transactional(readOnly = true)
    public boolean hasPin(Long driverId) {
        return appUserRepository.findByDriverId(driverId).isPresent();
    }

    /** Crée ou réinitialise le code d'accès du portail de pointage pour ce chauffeur. */
    @Transactional
    public void setPin(Long companyId, Long driverId, String pin) {
        Driver driver = requireDriver(companyId, driverId);
        AppUser account = appUserRepository.findByDriverId(driverId).orElseGet(AppUser::new);
        account.setUsername(loginUsername(driverId));
        account.setPassword(passwordEncoder.encode(pin));
        account.setRole("CHAUFFEUR");
        account.setDisplayName(driver.getFirstName() + " " + driver.getLastName());
        account.setEnabled(true);
        account.setCreatedAt(account.getCreatedAt() != null ? account.getCreatedAt() : LocalDateTime.now());
        account.setCompany(driver.getCompany());
        account.setDriver(driver);
        appUserRepository.save(account);
    }

    /** Supprime le compte du portail lié à ce chauffeur, s'il existe (avant suppression du chauffeur). */
    @Transactional
    public void deletePortalAccount(Long driverId) {
        appUserRepository.findByDriverId(driverId).ifPresent(appUserRepository::delete);
    }

    @Transactional(readOnly = true)
    public PointageRosterDto roster(String accessCode) {
        Company company = companyRepository.findByAccessCode(accessCode)
                .orElseThrow(() -> new ResourceNotFoundException("Portail introuvable"));
        List<PointageRosterDto.Driver> drivers = driverRepository.findByCompanyId(company.getId()).stream()
                .filter(Driver::isActive)
                .map(d -> new PointageRosterDto.Driver(d.getId(), d.getFirstName(), d.getLastName(), loginUsername(d.getId())))
                .toList();
        return new PointageRosterDto(company.getName(), drivers);
    }

    @Transactional(readOnly = true)
    public PointageStatusDto status(Long companyId, Long driverId) {
        Driver driver = driverRepository.findByIdAndCompanyId(driverId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Chauffeur introuvable"));
        List<PointageEvent> openShift = openShift(driverId);
        return toStatusDto(driver, openShift, LocalDateTime.now());
    }

    @Transactional
    public PointageStatusDto start(Long companyId, Long driverId) {
        Driver driver = requireDriver(companyId, driverId);
        List<PointageEvent> openShift = openShift(driverId);
        if (!openShift.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Un service est déjà en cours");
        }
        save(driver, Type.DEBUT);
        return toStatusDto(driver, openShift(driverId), LocalDateTime.now());
    }

    @Transactional
    public PointageStatusDto pause(Long companyId, Long driverId) {
        Driver driver = requireDriver(companyId, driverId);
        List<PointageEvent> openShift = openShift(driverId);
        requireState(openShift, false, "Aucun service en cours");
        save(driver, Type.PAUSE_DEBUT);
        return toStatusDto(driver, openShift(driverId), LocalDateTime.now());
    }

    @Transactional
    public PointageStatusDto resume(Long companyId, Long driverId) {
        Driver driver = requireDriver(companyId, driverId);
        List<PointageEvent> openShift = openShift(driverId);
        requireState(openShift, true, "Aucune pause en cours");
        save(driver, Type.PAUSE_FIN);
        return toStatusDto(driver, openShift(driverId), LocalDateTime.now());
    }

    @Transactional
    public PointageSummaryDto end(Long companyId, Long driverId) {
        Driver driver = requireDriver(companyId, driverId);
        List<PointageEvent> openShift = openShift(driverId);
        if (openShift.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun service en cours");
        }
        LocalDateTime now = LocalDateTime.now();
        save(driver, Type.FIN);

        LocalDateTime startedAt = openShift.get(0).getOccurredAt();
        long drivingSeconds = 0;
        long pauseSeconds = 0;
        int pauseCount = 0;
        boolean compliant = true;
        LocalDateTime segmentStart = startedAt;

        for (PointageEvent e : openShift) {
            if (e.getType() == Type.PAUSE_DEBUT) {
                long seg = Duration.between(segmentStart, e.getOccurredAt()).getSeconds();
                drivingSeconds += seg;
                if (seg > CONTINUOUS_DRIVING_LIMIT_SECONDS) compliant = false;
                segmentStart = e.getOccurredAt();
            } else if (e.getType() == Type.PAUSE_FIN) {
                pauseSeconds += Duration.between(segmentStart, e.getOccurredAt()).getSeconds();
                pauseCount++;
                segmentStart = e.getOccurredAt();
            }
        }
        // Dernier segment de conduite, jusqu'à la fin de service.
        long lastSeg = Duration.between(segmentStart, now).getSeconds();
        drivingSeconds += lastSeg;
        if (lastSeg > CONTINUOUS_DRIVING_LIMIT_SECONDS) compliant = false;

        return new PointageSummaryDto(startedAt, now, drivingSeconds, pauseSeconds, pauseCount, compliant);
    }

    private void requireState(List<PointageEvent> openShift, boolean expectPaused, String messageIfNot) {
        if (openShift.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Aucun service en cours");
        }
        boolean isPaused = openShift.get(openShift.size() - 1).getType() == Type.PAUSE_DEBUT;
        if (isPaused != expectPaused) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, messageIfNot);
        }
    }

    private Driver requireDriver(Long companyId, Long driverId) {
        return driverRepository.findByIdAndCompanyId(driverId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Chauffeur introuvable"));
    }

    private void save(Driver driver, Type type) {
        PointageEvent e = new PointageEvent();
        e.setCompany(driver.getCompany());
        e.setDriver(driver);
        e.setType(type);
        LocalDateTime now = LocalDateTime.now();
        e.setOccurredAt(now);
        e.setCreatedAt(now);
        eventRepository.save(e);
    }

    /** Événements du service actuellement ouvert (après la dernière fin de service). Vide si IDLE. */
    private List<PointageEvent> openShift(Long driverId) {
        List<PointageEvent> all = eventRepository.findByDriverIdOrderByOccurredAtAsc(driverId);
        int lastFin = -1;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).getType() == Type.FIN) lastFin = i;
        }
        return all.subList(lastFin + 1, all.size());
    }

    private PointageStatusDto toStatusDto(Driver driver, List<PointageEvent> openShift, LocalDateTime now) {
        String displayName = driver.getFirstName() + " " + driver.getLastName();
        if (openShift.isEmpty()) {
            return new PointageStatusDto("IDLE", displayName, null, null, 0, 0, List.of());
        }

        PointageEvent last = openShift.get(openShift.size() - 1);
        boolean paused = last.getType() == Type.PAUSE_DEBUT;
        LocalDateTime asOf = paused ? last.getOccurredAt() : now;

        LocalDateTime resumePoint = openShift.get(0).getOccurredAt();
        for (PointageEvent e : openShift) {
            if (e.getType() == Type.PAUSE_FIN) resumePoint = e.getOccurredAt();
        }
        long continuousDriving = Duration.between(resumePoint, asOf).getSeconds();
        long pauseSeconds = paused ? Duration.between(last.getOccurredAt(), now).getSeconds() : 0;

        List<PointageStatusDto.Event> events = openShift.stream()
                .map(e -> new PointageStatusDto.Event(e.getType().name(), e.getOccurredAt()))
                .toList();

        return new PointageStatusDto(
                paused ? "PAUSE" : "ACTIVE",
                displayName,
                openShift.get(0).getOccurredAt(),
                last.getOccurredAt(),
                continuousDriving,
                pauseSeconds,
                events);
    }
}
