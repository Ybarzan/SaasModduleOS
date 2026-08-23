package com.fleethub.service;

import com.fleethub.billing.StripeService;
import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.dto.AuditLogDto;
import com.fleethub.model.*;
import com.fleethub.repository.*;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Droits RGPD de l'utilisateur : portabilité (export JSON de toutes les données
 * du tenant) et effacement (suppression complète du compte, données associées
 * et résiliation de l'abonnement). L'opérateur plateforme (SAAS_ADMIN) ne peut
 * pas supprimer son propre compte via cette route.
 */
@Service
@RequiredArgsConstructor
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final CompanyRepository companyRepository;
    private final AppUserRepository userRepository;
    private final DriverRepository driverRepository;
    private final TruckRepository truckRepository;
    private final AssignmentRepository assignmentRepository;
    private final TripRepository tripRepository;
    private final DrivingEventRepository drivingEventRepository;
    private final TachographDayRepository tachographDayRepository;
    private final FuelRecordRepository fuelRecordRepository;
    private final CostRecordRepository costRecordRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final AuditLogRepository auditLogRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationRuleRepository notificationRuleRepository;
    private final IntegrationConfigRepository integrationConfigRepository;
    private final PasswordEncoder passwordEncoder;
    private final StripeService stripeService;

    @Transactional(readOnly = true)
    public Map<String, Object> exportData(Long companyId) {
        Company company = requireCompany(companyId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exportedAt", java.time.OffsetDateTime.now().toString());
        data.put("company", companyMap(company));
        data.put("users", userRepository.findByCompanyId(companyId).stream().map(this::userMap).toList());
        data.put("drivers", driverRepository.findByCompanyId(companyId).stream().map(this::driverMap).toList());
        data.put("trucks", truckRepository.findByCompanyId(companyId).stream().map(this::truckMap).toList());
        data.put("assignments", assignmentRepository.findAllFetch(companyId).stream().map(this::assignmentMap).toList());
        data.put("trips", tripRepository.findAllFetch(companyId).stream().map(this::tripMap).toList());
        data.put("drivingEvents", drivingEventRepository.findAllFetch(companyId).stream().map(this::drivingEventMap).toList());
        data.put("tachographDays", tachographDayRepository.findAllFetch(companyId).stream().map(this::tachographDayMap).toList());
        data.put("fuelRecords", fuelRecordRepository.findAllFetch(companyId).stream().map(this::fuelRecordMap).toList());
        data.put("costRecords", costRecordRepository.findAllFetch(companyId).stream().map(this::costRecordMap).toList());
        data.put("maintenanceRecords", maintenanceRepository.findAllFetch(companyId).stream().map(this::maintenanceMap).toList());
        data.put("integrationConfigs", integrationConfigRepository.findByCompanyId(companyId).stream()
                .map(c -> Map.of(
                        "id", c.getId(),
                        "provider", c.getProvider() != null ? c.getProvider().name() : null,
                        "baseUrl", c.getBaseUrl(),
                        "enabled", c.isEnabled(),
                        "hasApiKey", c.getApiKey() != null && !c.getApiKey().isBlank(),
                        "lastTestOk", c.getLastTestOk(),
                        "createdAt", c.getCreatedAt()))
                .toList());
        return data;
    }

    @Transactional(readOnly = true)
    public List<AuditLogDto> auditLogs(Long companyId) {
        return auditLogRepository.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(a -> new AuditLogDto(
                        a.getId(),
                        a.getCompany() != null ? a.getCompany().getId() : null,
                        a.getUserId(),
                        a.getUsername(),
                        a.getAction(),
                        a.getDetail(),
                        a.getIpAddress(),
                        a.getCreatedAt()))
                .toList();
    }

    @Transactional
    public void deleteAccount(Long companyId, String requesterUsername, String password) {
        Company company = requireCompany(companyId);
        AppUser requester = userRepository.findByUsername(requesterUsername)
                .filter(u -> u.getCompany() != null && u.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Vous n'êtes pas autorisé à supprimer ce compte"));
        if (!passwordEncoder.matches(password, requester.getPassword())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Mot de passe incorrect");
        }

        if (company.getSubscriptionId() != null) {
            try {
                stripeService.cancelSubscription(company);
            } catch (RuntimeException e) {
                log.warn("Résiliation Stripe impossible pour la société {} : {}", companyId, e.getMessage());
            }
        }

        drivingEventRepository.deleteByCompany_Id(companyId);
        tripRepository.deleteByCompany_Id(companyId);
        tachographDayRepository.deleteByCompany_Id(companyId);
        costRecordRepository.deleteByCompany_Id(companyId);
        fuelRecordRepository.deleteByCompany_Id(companyId);
        maintenanceRepository.deleteByCompany_Id(companyId);
        assignmentRepository.deleteByCompany_Id(companyId);
        driverRepository.deleteByCompany_Id(companyId);
        truckRepository.deleteByCompany_Id(companyId);
        userRepository.deleteByCompany_Id(companyId);
        notificationRepository.deleteByCompany_Id(companyId);
        notificationRuleRepository.deleteByCompany_Id(companyId);
        integrationConfigRepository.deleteByCompany_Id(companyId);
        auditLogRepository.deleteByCompany_Id(companyId);
        companyRepository.delete(company);
        log.info("Compte supprimé (RGPD) : société {} par {}", companyId, requesterUsername);
    }

    private Company requireCompany(Long companyId) {
        return companyRepository.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Société introuvable"));
    }

    private Map<String, Object> companyMap(Company c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("name", c.getName());
        m.put("plan", c.getPlan() != null ? c.getPlan().name() : null);
        m.put("status", c.getStatus() != null ? c.getStatus().name() : null);
        m.put("trialEndsAt", c.getTrialEndsAt());
        m.put("legalName", c.getLegalName());
        m.put("siret", c.getSiret());
        m.put("vatNumber", c.getVatNumber());
        m.put("address", c.getAddress());
        m.put("postalCode", c.getPostalCode());
        m.put("city", c.getCity());
        m.put("country", c.getCountry());
        m.put("contactEmail", c.getContactEmail());
        m.put("contactPhone", c.getContactPhone());
        m.put("createdAt", c.getCreatedAt());
        return m;
    }

    private Map<String, Object> userMap(AppUser u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("username", u.getUsername());
        m.put("email", u.getEmail());
        m.put("displayName", u.getDisplayName());
        m.put("role", u.getRole());
        m.put("enabled", u.isEnabled());
        m.put("createdAt", u.getCreatedAt());
        return m;
    }

    private Map<String, Object> driverMap(Driver d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("firstName", d.getFirstName());
        m.put("lastName", d.getLastName());
        m.put("licenseNumber", d.getLicenseNumber());
        m.put("phone", d.getPhone());
        m.put("email", d.getEmail());
        m.put("hireDate", d.getHireDate());
        m.put("active", d.isActive());
        return m;
    }

    private Map<String, Object> truckMap(Truck t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("registration", t.getRegistration());
        m.put("brand", t.getBrand());
        m.put("model", t.getModel());
        m.put("modelYear", t.getModelYear());
        m.put("truckType", t.getTruckType() != null ? t.getTruckType().name() : null);
        m.put("fuelType", t.getFuelType() != null ? t.getFuelType().name() : null);
        m.put("capacityTons", t.getCapacityTons());
        m.put("acquisitionDate", t.getAcquisitionDate());
        m.put("purchasePrice", t.getPurchasePrice());
        m.put("expectedConsumptionL100Km", t.getExpectedConsumptionL100Km());
        m.put("active", t.isActive());
        return m;
    }

    private Map<String, Object> assignmentMap(DriverTruckAssignment a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("driverId", a.getDriver() != null ? a.getDriver().getId() : null);
        m.put("truckId", a.getTruck() != null ? a.getTruck().getId() : null);
        m.put("startDate", a.getStartDate());
        m.put("endDate", a.getEndDate());
        m.put("active", a.isActive());
        return m;
    }

    private Map<String, Object> tripMap(Trip t) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("driverId", t.getDriver() != null ? t.getDriver().getId() : null);
        m.put("truckId", t.getTruck() != null ? t.getTruck().getId() : null);
        m.put("startTime", t.getStartTime());
        m.put("endTime", t.getEndTime());
        m.put("startKm", t.getStartKm());
        m.put("endKm", t.getEndKm());
        m.put("distanceKm", t.getDistanceKm());
        m.put("cargoWeightTons", t.getCargoWeightTons());
        m.put("loaded", t.isLoaded());
        m.put("status", t.getStatus() != null ? t.getStatus().name() : null);
        m.put("onTime", t.isOnTime());
        return m;
    }

    private Map<String, Object> drivingEventMap(DrivingEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("driverId", e.getDriver() != null ? e.getDriver().getId() : null);
        m.put("truckId", e.getTruck() != null ? e.getTruck().getId() : null);
        m.put("timestamp", e.getTimestamp());
        m.put("type", e.getType() != null ? e.getType().name() : null);
        m.put("severity", e.getSeverity());
        m.put("speedKph", e.getSpeedKph());
        m.put("durationSec", e.getDurationSec());
        m.put("latitude", e.getLatitude());
        m.put("longitude", e.getLongitude());
        return m;
    }

    private Map<String, Object> tachographDayMap(TachographDay d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("driverId", d.getDriver() != null ? d.getDriver().getId() : null);
        m.put("date", d.getDate());
        m.put("drivingHours", d.getDrivingHours());
        m.put("workHours", d.getWorkHours());
        m.put("restMinutes", d.getRestMinutes());
        m.put("compliant", d.isCompliant());
        return m;
    }

    private Map<String, Object> fuelRecordMap(FuelRecord f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", f.getId());
        m.put("truckId", f.getTruck() != null ? f.getTruck().getId() : null);
        m.put("date", f.getDate());
        m.put("liters", f.getLiters());
        m.put("amount", f.getAmount());
        m.put("odometerKm", f.getOdometerKm());
        return m;
    }

    private Map<String, Object> costRecordMap(CostRecord c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("truckId", c.getTruck() != null ? c.getTruck().getId() : null);
        m.put("driverId", c.getDriver() != null ? c.getDriver().getId() : null);
        m.put("billingMonth", c.getBillingMonth() != null ? YearMonth.from(c.getBillingMonth()).toString() : null);
        m.put("category", c.getCategory() != null ? c.getCategory().name() : null);
        m.put("amount", c.getAmount());
        return m;
    }

    private Map<String, Object> maintenanceMap(MaintenanceRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("truckId", r.getTruck() != null ? r.getTruck().getId() : null);
        m.put("scheduledDate", r.getScheduledDate());
        m.put("doneDate", r.getDoneDate());
        m.put("type", r.getType() != null ? r.getType().name() : null);
        m.put("planned", r.isPlanned());
        m.put("cost", r.getCost());
        m.put("doneOnTime", r.isDoneOnTime());
        m.put("status", r.getStatus() != null ? r.getStatus().name() : null);
        return m;
    }
}
