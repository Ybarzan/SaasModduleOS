package com.incokalk.service;

import com.incokalk.model.ExportDeclaration;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.ExportDeclarationRepository;
import com.incokalk.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("ExportDeclarationService — Tests update + getStats")
class ExportDeclarationServiceTest {

    @Mock ExportDeclarationRepository exportRepo;
    @Mock CompanyRepository companyRepo;
    ExportDeclarationService service;

    private UUID companyId;
    private UUID declId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        declId = UUID.randomUUID();
        TenantContext.set(companyId);
        service = new ExportDeclarationService(exportRepo, companyRepo, new DeclarationValidationService());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private ExportDeclaration buildDraft() {
        return ExportDeclaration.builder()
                .id(declId)
                .declarationNumber("EXP-2026-0001")
                .declarationType(ExportDeclaration.ExportType.AES)
                .status(ExportDeclaration.ExportStatus.DRAFT)
                .exporterEori("FR123456789")
                .destinationCountry("US")
                .hsCode("620443")
                .declaredValue(new BigDecimal("25000.00"))
                .currency("EUR")
                .netWeight(new BigDecimal("400.00"))
                .grossWeight(new BigDecimal("500.00"))
                .packagesCount(15)
                .goodsDescription("Textile exporté")
                .build();
    }

    // ── update ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Update DRAFT → fields copied, save called")
    void update_draftDeclaration_updatesFields() {
        ExportDeclaration existing = buildDraft();
        when(exportRepo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.of(existing));
        when(exportRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExportDeclaration updated = ExportDeclaration.builder()
                .destinationCountry("GB")
                .hsCode("620444")
                .declaredValue(new BigDecimal("30000.00"))
                .packagesCount(20)
                .build();

        ExportDeclaration result = service.update(declId, updated);

        assertThat(result.getDestinationCountry()).isEqualTo("GB");
        assertThat(result.getHsCode()).isEqualTo("620444");
        assertThat(result.getDeclaredValue()).isEqualByComparingTo(new BigDecimal("30000.00"));
        assertThat(result.getPackagesCount()).isEqualTo(20);
        assertThat(result.getExporterEori()).isEqualTo("FR123456789");
        verify(exportRepo).save(existing);
    }

    @Test
    @DisplayName("Update non-DRAFT (SUBMITTED) → throws")
    void update_submittedDeclaration_throws() {
        ExportDeclaration existing = buildDraft();
        existing.setStatus(ExportDeclaration.ExportStatus.SUBMITTED);
        when(exportRepo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(declId, ExportDeclaration.builder().destinationCountry("GB").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("brouillon");

        verify(exportRepo, never()).save(any());
    }

    @Test
    @DisplayName("Update non-DRAFT (VALIDATED) → throws")
    void update_validatedDeclaration_throws() {
        ExportDeclaration existing = buildDraft();
        existing.setStatus(ExportDeclaration.ExportStatus.VALIDATED);
        when(exportRepo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(declId, ExportDeclaration.builder().destinationCountry("GB").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("brouillon");
    }

    @Test
    @DisplayName("Update déclaration introuvable → throws")
    void update_notFound_throws() {
        when(exportRepo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(declId, ExportDeclaration.builder().build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── updateStatus ────────────────────────────────────────────────────

    @Test
    @DisplayName("DRAFT → SUBMITTED → VALIDATED (transitions valides)")
    void updateStatus_draftToSubmittedToValidated() {
        ExportDeclaration existing = buildDraft();
        when(exportRepo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.of(existing));
        when(exportRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExportDeclaration result = service.updateStatus(declId, ExportDeclaration.ExportStatus.SUBMITTED);
        assertThat(result.getStatus()).isEqualTo(ExportDeclaration.ExportStatus.SUBMITTED);
        assertThat(result.getSubmittedAt()).isNotNull();

        result = service.updateStatus(declId, ExportDeclaration.ExportStatus.VALIDATED);
        assertThat(result.getStatus()).isEqualTo(ExportDeclaration.ExportStatus.VALIDATED);
        assertThat(result.getValidatedAt()).isNotNull();
    }

    @Test
    @DisplayName("DRAFT → SUBMITTED → REJECTED (transition rejet)")
    void updateStatus_draftToSubmittedToRejected() {
        ExportDeclaration existing = buildDraft();
        when(exportRepo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.of(existing));
        when(exportRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateStatus(declId, ExportDeclaration.ExportStatus.SUBMITTED);
        ExportDeclaration result = service.updateStatus(declId, ExportDeclaration.ExportStatus.REJECTED);

        assertThat(result.getStatus()).isEqualTo(ExportDeclaration.ExportStatus.REJECTED);
        assertThat(result.getRejectedAt()).isNotNull();
    }

    @Test
    @DisplayName("DRAFT → VALIDATED (transition invalide) → throws")
    void updateStatus_draftToValidated_throws() {
        ExportDeclaration existing = buildDraft();
        when(exportRepo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateStatus(declId, ExportDeclaration.ExportStatus.VALIDATED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalide");
    }

    @Test
    @DisplayName("VALIDATED → * (terminal) → throws")
    void updateStatus_validatedTerminal_throws() {
        ExportDeclaration existing = buildDraft();
        existing.setStatus(ExportDeclaration.ExportStatus.VALIDATED);
        when(exportRepo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateStatus(declId, ExportDeclaration.ExportStatus.SUBMITTED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── getStats ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getStats → retourne tous les comptes par statut")
    void getStats_returnsAllStatusCounts() {
        for (ExportDeclaration.ExportStatus status : ExportDeclaration.ExportStatus.values()) {
            when(exportRepo.countByCompanyIdAndStatus(companyId, status))
                    .thenReturn((long) status.ordinal() + 1);
        }

        var stats = service.getStats();

        assertThat(stats).hasSize(ExportDeclaration.ExportStatus.values().length);
        assertThat(stats.get("DRAFT")).isEqualTo(1L);
        assertThat(stats.get("SUBMITTED")).isEqualTo(2L);
        assertThat(stats.get("VALIDATED")).isEqualTo(3L);
        assertThat(stats.get("REJECTED")).isEqualTo(4L);
    }
}
