package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.DeniedPartyCheck;
import com.incokalk.model.SanctionedEntity;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.DeniedPartyCheckRepository;
import com.incokalk.repository.SanctionedEntityRepository;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeniedPartyScreeningService {

    private final DeniedPartyCheckRepository deniedPartyCheckRepo;
    private final SanctionedEntityRepository sanctionedEntityRepo;
    private final CompanyRepository companyRepo;
    private final NotificationService notificationService;

    @Transactional
    public DeniedPartyCheck screen(String name, String countryCode,
                                   DeniedPartyCheck.CheckType checkType, UUID userId) {
        UUID companyId = TenantContext.get();
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found"));

        DeniedPartyCheck.CheckResult result = DeniedPartyCheck.CheckResult.CLEAR;
        DeniedPartyCheck.RiskLevel riskLevel = DeniedPartyCheck.RiskLevel.LOW;
        String matchedListName = null;
        String matchedEntryId = null;
        String matchedEntryDetails = null;

        List<SanctionedEntity> nameMatches = sanctionedEntityRepo
                .findByNameContainingIgnoreCaseAndIsActiveTrue(name);

        for (SanctionedEntity entity : nameMatches) {
            if (entity.getName().equalsIgnoreCase(name)) {
                result = DeniedPartyCheck.CheckResult.BLOCKED;
                riskLevel = DeniedPartyCheck.RiskLevel.CRITICAL;
                matchedListName = entity.getListSource();
                matchedEntryId = entity.getEntryId();
                matchedEntryDetails = buildEntryDetails(entity);
                break;
            }
        }

        if (result == DeniedPartyCheck.CheckResult.CLEAR && entityAliasesContain(nameMatches, name)) {
            result = DeniedPartyCheck.CheckResult.BLOCKED;
            riskLevel = DeniedPartyCheck.RiskLevel.CRITICAL;
            for (SanctionedEntity entity : nameMatches) {
                if (matchesAlias(entity, name)) {
                    matchedListName = entity.getListSource();
                    matchedEntryId = entity.getEntryId();
                    matchedEntryDetails = buildEntryDetails(entity);
                    break;
                }
            }
        }

        if (result == DeniedPartyCheck.CheckResult.CLEAR && !nameMatches.isEmpty()) {
            result = DeniedPartyCheck.CheckResult.MATCH;
            riskLevel = DeniedPartyCheck.RiskLevel.HIGH;
            SanctionedEntity first = nameMatches.get(0);
            matchedListName = first.getListSource();
            matchedEntryId = first.getEntryId();
            matchedEntryDetails = buildEntryDetails(first);
        }

        if (result == DeniedPartyCheck.CheckResult.CLEAR && countryCode != null && !countryCode.isBlank()) {
            List<SanctionedEntity> countryMatches = sanctionedEntityRepo
                    .findByCountryCodeAndIsActiveTrue(countryCode);
            if (!countryMatches.isEmpty()) {
                result = DeniedPartyCheck.CheckResult.POSSIBLE_MATCH;
                riskLevel = DeniedPartyCheck.RiskLevel.MEDIUM;
                matchedEntryDetails = countryMatches.size() + " sanctioned entities found for country " + countryCode;
            }
        }

        DeniedPartyCheck check = DeniedPartyCheck.builder()
                .company(company)
                .checkedName(name)
                .checkType(checkType)
                .result(result)
                .matchedListName(matchedListName)
                .matchedEntryId(matchedEntryId)
                .matchedEntryDetails(matchedEntryDetails)
                .riskLevel(riskLevel)
                .countryCode(countryCode)
                .checkedByUserId(userId != null ? userId : companyId)
                .build();

        deniedPartyCheckRepo.save(check);
        log.info("DPS screening for '{}' result={}", name, result);

        if (riskLevel == DeniedPartyCheck.RiskLevel.CRITICAL || riskLevel == DeniedPartyCheck.RiskLevel.HIGH) {
            try {
                notificationService.onDpsAlert(
                        check.getId(), name, riskLevel.name(),
                        matchedListName, matchedEntryDetails, companyId);
                log.info("DPS alert notification sent for '{}' (risk={})", name, riskLevel);
            } catch (Exception e) {
                log.warn("Failed to send DPS alert notification: {}", e.getMessage());
            }
        }

        return check;
    }

    public List<DeniedPartyCheck> getHistory() {
        UUID companyId = TenantContext.get();
        return deniedPartyCheckRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    public DeniedPartyCheck getById(UUID id) {
        UUID companyId = TenantContext.get();
        return deniedPartyCheckRepo.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new IllegalArgumentException("Check not found"));
    }

    public Map<String, Object> getStats() {
        UUID companyId = TenantContext.get();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", deniedPartyCheckRepo.countByCompanyIdAndResult(companyId, DeniedPartyCheck.CheckResult.CLEAR)
                + deniedPartyCheckRepo.countByCompanyIdAndResult(companyId, DeniedPartyCheck.CheckResult.MATCH)
                + deniedPartyCheckRepo.countByCompanyIdAndResult(companyId, DeniedPartyCheck.CheckResult.POSSIBLE_MATCH)
                + deniedPartyCheckRepo.countByCompanyIdAndResult(companyId, DeniedPartyCheck.CheckResult.BLOCKED));
        stats.put("CLEAR", deniedPartyCheckRepo.countByCompanyIdAndResult(companyId, DeniedPartyCheck.CheckResult.CLEAR));
        stats.put("MATCH", deniedPartyCheckRepo.countByCompanyIdAndResult(companyId, DeniedPartyCheck.CheckResult.MATCH));
        stats.put("POSSIBLE_MATCH", deniedPartyCheckRepo.countByCompanyIdAndResult(companyId, DeniedPartyCheck.CheckResult.POSSIBLE_MATCH));
        stats.put("BLOCKED", deniedPartyCheckRepo.countByCompanyIdAndResult(companyId, DeniedPartyCheck.CheckResult.BLOCKED));
        return stats;
    }

    public List<SanctionedEntity> getSanctionedEntities() {
        return sanctionedEntityRepo.findByIsActiveTrue();
    }

    public List<DeniedPartyCheck> getAlerts() {
        UUID companyId = TenantContext.get();
        return deniedPartyCheckRepo.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .filter(c -> c.getRiskLevel() == DeniedPartyCheck.RiskLevel.CRITICAL
                        || c.getRiskLevel() == DeniedPartyCheck.RiskLevel.HIGH)
                .toList();
    }

    private boolean entityAliasesContain(List<SanctionedEntity> entities, String name) {
        return entities.stream().anyMatch(e -> matchesAlias(e, name));
    }

    private boolean matchesAlias(SanctionedEntity entity, String name) {
        if (entity.getAliases() == null || entity.getAliases().isBlank()) {
            return false;
        }
        return Arrays.stream(entity.getAliases().split(","))
                .map(String::trim)
                .anyMatch(alias -> alias.equalsIgnoreCase(name));
    }

    private String buildEntryDetails(SanctionedEntity entity) {
        return "List: " + entity.getListSource()
                + " | Entry: " + entity.getEntryId()
                + " | Name: " + entity.getName()
                + " | Type: " + entity.getEntityType()
                + (entity.getCountryCode() != null ? " | Country: " + entity.getCountryCode() : "")
                + (entity.getReason() != null ? " | Reason: " + entity.getReason() : "")
                + (entity.getProgram() != null ? " | Program: " + entity.getProgram() : "");
    }
}
