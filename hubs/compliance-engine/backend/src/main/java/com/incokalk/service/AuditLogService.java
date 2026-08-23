package com.incokalk.service;

import com.incokalk.model.AuditLog;
import com.incokalk.model.Company;
import com.incokalk.repository.AuditLogRepository;
import com.incokalk.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepo;
    private final CompanyRepository companyRepo;

    @Transactional
    public void log(UUID companyId, UUID userId, String userEmail, String userRole,
                    String action, String entityType, UUID entityId, String entityName,
                    String details, String ipAddress, String userAgent) {
        try {
            Company company = companyRepo.findById(companyId).orElse(null);
            if (company == null) {
                log.warn("Audit log skipped: company {} not found", companyId);
                return;
            }

            AuditLog entry = AuditLog.builder()
                    .company(company)
                    .userId(userId)
                    .userEmail(userEmail)
                    .userRole(userRole)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .entityName(entityName)
                    .details(details)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .build();

            auditLogRepo.save(entry);
        } catch (Exception e) {
            log.error("Failed to create audit log: {}", e.getMessage(), e);
        }
    }

    public Page<AuditLog> listByCompany(UUID companyId, int page, int size) {
        return auditLogRepo.findByCompanyIdOrderByCreatedAtDesc(companyId, PageRequest.of(page, size));
    }

    public Page<AuditLog> listByCompanyAndAction(UUID companyId, String action, int page, int size) {
        return auditLogRepo.findByCompanyIdAndActionOrderByCreatedAtDesc(companyId, action, PageRequest.of(page, size));
    }

    public Page<AuditLog> listByCompanyAndEntity(UUID companyId, String entityType, int page, int size) {
        return auditLogRepo.findByCompanyIdAndEntityTypeOrderByCreatedAtDesc(companyId, entityType, PageRequest.of(page, size));
    }

    public Page<AuditLog> listByCompanyAndUser(UUID companyId, UUID userId, int page, int size) {
        return auditLogRepo.findByCompanyIdAndUserIdOrderByCreatedAtDesc(companyId, userId, PageRequest.of(page, size));
    }

    public Page<AuditLog> listByCompanyAndDateRange(UUID companyId, LocalDateTime from, LocalDateTime to, int page, int size) {
        return auditLogRepo.findByCompanyIdAndCreatedAtBetweenOrderByCreatedAtDesc(companyId, from, to, PageRequest.of(page, size));
    }

    public Map<String, Object> getStats(UUID companyId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", auditLogRepo.countByCompanyId(companyId));

        Map<String, Long> byAction = new LinkedHashMap<>();
        for (Object[] row : auditLogRepo.countByActionGrouped(companyId)) {
            byAction.put((String) row[0], (Long) row[1]);
        }
        stats.put("byAction", byAction);

        Map<String, Long> byEntity = new LinkedHashMap<>();
        for (Object[] row : auditLogRepo.countByEntityGrouped(companyId)) {
            byEntity.put((String) row[0], (Long) row[1]);
        }
        stats.put("byEntity", byEntity);

        return stats;
    }
}
