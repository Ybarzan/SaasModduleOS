package com.fleethub.service;

import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.dto.NotificationDto;
import com.fleethub.dto.NotificationRuleDto;
import com.fleethub.model.*;
import com.fleethub.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Alertes & notifications du tenant : règles configurables par société
 * (créées par défaut au premier balayage) et génération de notifications à
 * partir des données de flotte (maintenance à échéance, non-conformité
 * tachygraphe, temps de conduite, usage anormal). La génération est idempotente
 * (au maximum une notification par entité et par type sur 24 h).
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int DEDUP_HOURS = 24;

    private final NotificationRepository notificationRepository;
    private final NotificationRuleRepository ruleRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final TachographDayRepository tachographDayRepository;
    private final DrivingEventRepository drivingEventRepository;
    private final DriverRepository driverRepository;
    private final TruckRepository truckRepository;
    private final CompanyRepository companyRepository;

    @Transactional
    public List<NotificationDto> listAndScan(Long companyId) {
        scan(companyId);
        return notificationRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long companyId) {
        return notificationRepository.countByCompanyIdAndReadFalse(companyId);
    }

    @Transactional
    public void markRead(Long companyId, Long id) {
        Notification n = notificationRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification introuvable"));
        if (!n.isRead()) {
            n.setRead(true);
            notificationRepository.save(n);
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationRuleDto> listRules(Long companyId) {
        ensureDefaultRules(companyId);
        return ruleRepository.findByCompanyId(companyId).stream()
                .map(this::toRuleDto)
                .toList();
    }

    @Transactional
    public NotificationRuleDto saveRule(Long companyId, NotificationRuleDto dto) {
        if (dto.type() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le type de règle est requis");
        }
        NotificationRule rule;
        if (dto.id() != null) {
            rule = ruleRepository.findByIdAndCompanyId(dto.id(), companyId)
                    .orElseThrow(() -> new ResourceNotFoundException("Règle introuvable"));
        } else {
            rule = new NotificationRule();
            rule.setCompany(companyRepository.getReferenceById(companyId));
        }
        rule.setType(dto.type());
        rule.setThreshold(dto.threshold());
        rule.setEnabled(dto.enabled());
        return toRuleDto(ruleRepository.save(rule));
    }

    @Transactional
    public void deleteRule(Long companyId, Long id) {
        NotificationRule rule = ruleRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Règle introuvable"));
        ruleRepository.delete(rule);
    }

    // ---- Génération ----

    @Transactional
    public int scan(Long companyId) {
        ensureDefaultRules(companyId);
        List<NotificationRule> rules = ruleRepository.findByCompanyIdAndEnabledTrue(companyId);
        int created = 0;
        created += scanMaintenance(companyId, rules);
        created += scanTachograph(companyId, rules);
        created += scanDrivingTime(companyId, rules);
        created += scanUsage(companyId, rules);
        return created;
    }

    private int scanMaintenance(Long companyId, List<NotificationRule> rules) {
        NotificationRule rule = findRule(rules, NotificationRule.AlertType.MAINTENANCE_ECHEANCE);
        if (rule == null) return 0;
        int horizon = rule.getThreshold() == null ? 7 : rule.getThreshold().intValue();
        LocalDate limit = LocalDate.now().plusDays(horizon);
        int created = 0;
        for (MaintenanceRecord m : maintenanceRepository.findAllFetch(companyId)) {
            if (m.getStatus() != MaintenanceRecord.MaintenanceStatus.PLANIFIE) continue;
            if (m.getScheduledDate().isAfter(limit)) continue;
            Truck truck = m.getTruck();
            if (alreadySent(companyId, NotificationRule.AlertType.MAINTENANCE_ECHEANCE, truck.getId())) continue;
            create(companyId, NotificationRule.AlertType.MAINTENANCE_ECHEANCE,
                    "Entretien à échéance — " + truck.getRegistration(),
                    "L'entretien « " + label(m.getType()) + " » du camion " + truck.getRegistration()
                            + " est planifié au " + m.getScheduledDate() + ".",
                    truck.getId(), "TRUCK");
            created++;
        }
        return created;
    }

    private int scanTachograph(Long companyId, List<NotificationRule> rules) {
        if (findRule(rules, NotificationRule.AlertType.TACHYGRAPHIE_NON_CONFORME) == null) return 0;
        LocalDate since = LocalDate.now().minusDays(7);
        int created = 0;
        for (Driver driver : driverRepository.findByCompanyId(companyId)) {
            long nonCompliant = tachographDayRepository.findByDriverAndDateAfter(driver, since).stream()
                    .filter(d -> !d.isCompliant())
                    .count();
            if (nonCompliant > 0
                    && !alreadySent(companyId, NotificationRule.AlertType.TACHYGRAPHIE_NON_CONFORME, driver.getId())) {
                create(companyId, NotificationRule.AlertType.TACHYGRAPHIE_NON_CONFORME,
                        "Non-conformité tachygraphe",
                        driver.getFirstName() + " " + driver.getLastName() + " a "
                                + nonCompliant + " jour(s) non conforme(s) cette semaine.",
                        driver.getId(), "DRIVER");
                created++;
            }
        }
        return created;
    }

    private int scanDrivingTime(Long companyId, List<NotificationRule> rules) {
        NotificationRule rule = findRule(rules, NotificationRule.AlertType.TEMPS_CONDUITE);
        if (rule == null) return 0;
        double maxHours = rule.getThreshold() == null ? 48.0 : rule.getThreshold();
        LocalDate since = LocalDate.now().minusDays(7);
        int created = 0;
        for (Driver driver : driverRepository.findByCompanyId(companyId)) {
            double hours = tachographDayRepository.findByDriverAndDateAfter(driver, since).stream()
                    .mapToDouble(TachographDay::getDrivingHours)
                    .sum();
            if (hours > maxHours
                    && !alreadySent(companyId, NotificationRule.AlertType.TEMPS_CONDUITE, driver.getId())) {
                create(companyId, NotificationRule.AlertType.TEMPS_CONDUITE,
                        "Temps de conduite dépassé",
                        driver.getFirstName() + " " + driver.getLastName() + " a cumulé "
                                + Math.round(hours) + " h de conduite sur 7 jours (limite "
                                + maxHours + " h).",
                        driver.getId(), "DRIVER");
                created++;
            }
        }
        return created;
    }

    private int scanUsage(Long companyId, List<NotificationRule> rules) {
        if (findRule(rules, NotificationRule.AlertType.USAGE_ANORMAL) == null) return 0;
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        int created = 0;
        for (Truck truck : truckRepository.findByCompanyId(companyId)) {
            long severe = drivingEventRepository.findByTruckAndTimestampAfter(truck, since).stream()
                    .filter(e -> e.getSeverity() >= 8)
                    .count();
            if (severe > 0
                    && !alreadySent(companyId, NotificationRule.AlertType.USAGE_ANORMAL, truck.getId())) {
                create(companyId, NotificationRule.AlertType.USAGE_ANORMAL,
                        "Usage anormal détecté",
                        truck.getRegistration() + " : " + severe + " événement(s) grave(s) "
                                + "(freinage brusque, excès de vitesse…) sur les dernières 24 h.",
                        truck.getId(), "TRUCK");
                created++;
            }
        }
        return created;
    }

    private boolean alreadySent(Long companyId, NotificationRule.AlertType type, Long entityId) {
        return notificationRepository.existsByCompanyIdAndTypeAndEntityIdAndCreatedAtAfter(
                companyId, type, entityId, LocalDateTime.now().minusHours(DEDUP_HOURS));
    }

    private void create(Long companyId, NotificationRule.AlertType type, String title, String message,
                        Long entityId, String entityType) {
        Notification n = new Notification();
        n.setCompany(companyRepository.getReferenceById(companyId));
        n.setType(type);
        n.setTitle(title);
        n.setMessage(message);
        n.setEntityId(entityId);
        n.setEntityType(entityType);
        n.setRead(false);
        n.setCreatedAt(LocalDateTime.now());
        notificationRepository.save(n);
    }

    private void ensureDefaultRules(Long companyId) {
        if (ruleRepository.findByCompanyId(companyId).isEmpty()) {
            saveRule(companyId, new NotificationRuleDto(null, NotificationRule.AlertType.MAINTENANCE_ECHEANCE, 7.0, true));
            saveRule(companyId, new NotificationRuleDto(null, NotificationRule.AlertType.TACHYGRAPHIE_NON_CONFORME, null, true));
            saveRule(companyId, new NotificationRuleDto(null, NotificationRule.AlertType.TEMPS_CONDUITE, 48.0, true));
            saveRule(companyId, new NotificationRuleDto(null, NotificationRule.AlertType.USAGE_ANORMAL, null, true));
        }
    }

    private NotificationRule findRule(List<NotificationRule> rules, NotificationRule.AlertType type) {
        return rules.stream().filter(r -> r.getType() == type).findFirst().orElse(null);
    }

    private String label(MaintenanceRecord.MaintenanceType type) {
        return switch (type) {
            case VIDANGE -> "vidange";
            case FREINS -> "freins";
            case PNEUS -> "pneumatiques";
            case REVISION -> "révision";
            case CONTROLE_TECHNIQUE -> "contrôle technique";
            case REPARATION -> "réparation";
        };
    }

    private NotificationDto toDto(Notification n) {
        return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getMessage(),
                n.getEntityId(), n.getEntityType(), n.isRead(), n.getCreatedAt());
    }

    private NotificationRuleDto toRuleDto(NotificationRule r) {
        return new NotificationRuleDto(r.getId(), r.getType(), r.getThreshold(), r.isEnabled());
    }
}
