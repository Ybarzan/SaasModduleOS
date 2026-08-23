package com.incokalk.service;

import com.incokalk.model.AuditLog;
import com.incokalk.model.Company;
import com.incokalk.repository.AuditLogRepository;
import com.incokalk.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AuditLogService — Tests unitaires")
class AuditLogServiceTest {

    @Mock AuditLogRepository auditLogRepo;
    @Mock CompanyRepository companyRepo;
    @InjectMocks AuditLogService service;

    UUID companyId;
    UUID userId;
    Company company;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        userId = UUID.randomUUID();
        company = Company.builder().id(companyId).build();
    }

    @Test
    @DisplayName("log → sauvegarde un audit log")
    void log_success() {
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        service.log(companyId, userId, "user@test.com", "ADMIN",
                "LOGIN", "USER", userId, "John", "Connexion réussie", "127.0.0.1", "Chrome");
        verify(auditLogRepo).save(any(AuditLog.class));
    }

    @Test
    @DisplayName("log → société non trouvée → skip")
    void log_companyNotFound() {
        when(companyRepo.findById(companyId)).thenReturn(Optional.empty());
        service.log(companyId, userId, "user@test.com", "ADMIN",
                "LOGIN", "USER", userId, "John", "", "", "");
        verify(auditLogRepo, never()).save(any());
    }

    @Test
    @DisplayName("listByCompany → retourne page")
    void listByCompany() {
        PageRequest pr = PageRequest.of(0, 10);
        Page<AuditLog> page = new PageImpl<>(List.of());
        when(auditLogRepo.findByCompanyIdOrderByCreatedAtDesc(companyId, pr)).thenReturn(page);
        assertThat(service.listByCompany(companyId, 0, 10)).isEqualTo(page);
    }

    @Test
    @DisplayName("listByCompanyAndAction → retourne page")
    void listByCompanyAndAction() {
        PageRequest pr = PageRequest.of(0, 10);
        Page<AuditLog> page = new PageImpl<>(List.of());
        when(auditLogRepo.findByCompanyIdAndActionOrderByCreatedAtDesc(companyId, "LOGIN", pr)).thenReturn(page);
        assertThat(service.listByCompanyAndAction(companyId, "LOGIN", 0, 10)).isEqualTo(page);
    }

    @Test
    @DisplayName("listByCompanyAndDateRange → retourne page")
    void listByCompanyAndDateRange() {
        PageRequest pr = PageRequest.of(0, 10);
        LocalDateTime from = LocalDateTime.now().minusDays(7);
        LocalDateTime to = LocalDateTime.now();
        when(auditLogRepo.findByCompanyIdAndCreatedAtBetweenOrderByCreatedAtDesc(companyId, from, to, pr))
                .thenReturn(Page.empty());
        assertThat(service.listByCompanyAndDateRange(companyId, from, to, 0, 10)).isNotNull();
    }

    @Test
    @DisplayName("getStats → contient total et byAction")
    void getStats() {
        when(auditLogRepo.countByCompanyId(companyId)).thenReturn(42L);
        List<Object[]> actions = new ArrayList<>();
        actions.add(new Object[]{"LOGIN", 20L});
        when(auditLogRepo.countByActionGrouped(companyId)).thenReturn(actions);
        List<Object[]> entities = new ArrayList<>();
        entities.add(new Object[]{"USER", 42L});
        when(auditLogRepo.countByEntityGrouped(companyId)).thenReturn(entities);
        var stats = service.getStats(companyId);
        assertThat(stats.get("total")).isEqualTo(42L);
    }
}
