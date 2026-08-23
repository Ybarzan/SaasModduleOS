package com.incokalk.service;

import com.incokalk.model.DebDeclaration;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.DebDeclarationRepository;
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

@DisplayName("DebDeclarationService — Tests update + getStats")
class DebDeclarationServiceTest {

    @Mock DebDeclarationRepository debRepo;
    @Mock CompanyRepository companyRepo;
    DebDeclarationService service;

    private UUID companyId;
    private UUID declId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        declId = UUID.randomUUID();
        TenantContext.set(companyId);
        service = new DebDeclarationService(debRepo, companyRepo, new DeclarationValidationService());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private DebDeclaration buildDraft() {
        return DebDeclaration.builder()
                .id(declId)
                .declarationNumber("DEB-2026-07-001")
                .declarationType(DebDeclaration.DebType.DEB_INTRODUCTION)
                .status(DebDeclaration.DebStatus.DRAFT)
                .period("2026-07")
                .partnerCountry("DE")
                .natureOfTransaction("10")
                .modeOfTransport("3")
                .hsCode8("62044300")
                .netMass(new BigDecimal("120.50"))
                .statisticalValue(new BigDecimal("8500.00"))
                .goodsDescription("Vêtements importés")
                .build();
    }

    // ── update ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Update DRAFT → fields copied, save called")
    void update_draftDeclaration_updatesFields() {
        DebDeclaration existing = buildDraft();
        when(debRepo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.of(existing));
        when(debRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DebDeclaration updated = DebDeclaration.builder()
                .partnerCountry("IT")
                .hsCode8("62044400")
                .netMass(new BigDecimal("200.00"))
                .statisticalValue(new BigDecimal("12000.00"))
                .build();

        DebDeclaration result = service.update(declId, updated);

        assertThat(result.getPartnerCountry()).isEqualTo("IT");
        assertThat(result.getHsCode8()).isEqualTo("62044400");
        assertThat(result.getNetMass()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(result.getStatisticalValue()).isEqualByComparingTo(new BigDecimal("12000.00"));
        assertThat(result.getPeriod()).isEqualTo("2026-07");
        verify(debRepo).save(existing);
    }

    @Test
    @DisplayName("Update non-DRAFT (VALIDATED) → throws")
    void update_validatedDeclaration_throws() {
        DebDeclaration existing = buildDraft();
        existing.setStatus(DebDeclaration.DebStatus.VALIDATED);
        when(debRepo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(declId, DebDeclaration.builder().partnerCountry("IT").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("brouillon");

        verify(debRepo, never()).save(any());
    }

    @Test
    @DisplayName("Update non-DRAFT (SUBMITTED) → throws")
    void update_submittedDeclaration_throws() {
        DebDeclaration existing = buildDraft();
        existing.setStatus(DebDeclaration.DebStatus.SUBMITTED);
        when(debRepo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(declId, DebDeclaration.builder().partnerCountry("IT").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("brouillon");
    }

    @Test
    @DisplayName("Update avec champs null → aucun champ modifié")
    void update_nullFields_noChange() {
        DebDeclaration existing = buildDraft();
        when(debRepo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.of(existing));
        when(debRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.update(declId, DebDeclaration.builder().build());

        assertThat(existing.getPartnerCountry()).isEqualTo("DE");
        assertThat(existing.getHsCode8()).isEqualTo("62044300");
    }

    @Test
    @DisplayName("Update déclaration introuvable → throws")
    void update_notFound_throws() {
        when(debRepo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(declId, DebDeclaration.builder().build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── updateStatus ────────────────────────────────────────────────────

    @Test
    @DisplayName("DRAFT → VALIDATED → SUBMITTED (transitions valides)")
    void updateStatus_draftToValidatedToSubmitted() {
        DebDeclaration existing = buildDraft();
        when(debRepo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.of(existing));
        when(debRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DebDeclaration result = service.updateStatus(declId, DebDeclaration.DebStatus.VALIDATED);
        assertThat(result.getStatus()).isEqualTo(DebDeclaration.DebStatus.VALIDATED);

        result = service.updateStatus(declId, DebDeclaration.DebStatus.SUBMITTED);
        assertThat(result.getStatus()).isEqualTo(DebDeclaration.DebStatus.SUBMITTED);
        assertThat(result.getSubmittedAt()).isNotNull();
    }

    @Test
    @DisplayName("DRAFT → SUBMITTED (transition invalide) → throws")
    void updateStatus_draftToSubmitted_throws() {
        DebDeclaration existing = buildDraft();
        when(debRepo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateStatus(declId, DebDeclaration.DebStatus.SUBMITTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalide");
    }

    @Test
    @DisplayName("SUBMITTED → * (terminal) → throws")
    void updateStatus_submittedTerminal_throws() {
        DebDeclaration existing = buildDraft();
        existing.setStatus(DebDeclaration.DebStatus.SUBMITTED);
        when(debRepo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateStatus(declId, DebDeclaration.DebStatus.VALIDATED))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── getStats ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getStats → retourne tous les comptes")
    void getStats_returnsAllCounts() {
        when(debRepo.countByCompanyId(companyId)).thenReturn(30L);
        when(debRepo.countByCompanyIdAndStatus(companyId, DebDeclaration.DebStatus.DRAFT)).thenReturn(8L);
        when(debRepo.countByCompanyIdAndStatus(companyId, DebDeclaration.DebStatus.VALIDATED)).thenReturn(12L);
        when(debRepo.countByCompanyIdAndStatus(companyId, DebDeclaration.DebStatus.SUBMITTED)).thenReturn(10L);

        var stats = service.getStats();

        assertThat(stats.get("total")).isEqualTo(30L);
        assertThat(stats.get("draft")).isEqualTo(8L);
        assertThat(stats.get("validated")).isEqualTo(12L);
        assertThat(stats.get("submitted")).isEqualTo(10L);
    }
}
