package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.CustomsDeclaration;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.CustomsDeclarationRepository;
import com.incokalk.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("CustomsDeclarationService — Tests update + getStats")
class CustomsDeclarationServiceTest {

    @Mock CustomsDeclarationRepository declarationRepo;
    @Mock CompanyRepository companyRepo;
    @Mock com.incokalk.repository.EoriNumberRepository eoriRepo;
    @InjectMocks CustomsDeclarationService service;

    private UUID companyId;
    private UUID declId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        declId = UUID.randomUUID();
        TenantContext.set(companyId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private CustomsDeclaration buildDraft() {
        return CustomsDeclaration.builder()
                .id(declId)
                .declarationNumber("DAU-2026-0001")
                .declarationType(CustomsDeclaration.DeclarationType.DAU_IMPORT)
                .status(CustomsDeclaration.DeclarationStatus.DRAFT)
                .customsOffice("Paris CDG")
                .customsRegime("4000")
                .customsCode("1001")
                .originCountry("VN")
                .destinationCountry("FR")
                .declaredValue(new BigDecimal("15000.00"))
                .currency("EUR")
                .hsCode("620443")
                .goodsDescription("Robes")
                .netWeight(new BigDecimal("250.00"))
                .grossWeight(new BigDecimal("300.00"))
                .packages(10)
                .notes("Test note")
                .build();
    }

    // ── update ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Update DRAFT → fields copied, save called")
    void update_draftDeclaration_updatesFields() {
        CustomsDeclaration existing = buildDraft();
        when(declarationRepo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.of(existing));
        when(declarationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomsDeclaration updated = CustomsDeclaration.builder()
                .customsOffice("Marseille")
                .hsCode("620444")
                .declaredValue(new BigDecimal("20000.00"))
                .packages(15)
                .build();

        CustomsDeclaration result = service.update(declId, updated);

        assertThat(result.getCustomsOffice()).isEqualTo("Marseille");
        assertThat(result.getHsCode()).isEqualTo("620444");
        assertThat(result.getDeclaredValue()).isEqualByComparingTo(new BigDecimal("20000.00"));
        assertThat(result.getPackages()).isEqualTo(15);
        assertThat(result.getOriginCountry()).isEqualTo("VN");
        verify(declarationRepo).save(existing);
    }

    @Test
    @DisplayName("Update non-DRAFT → throws IllegalArgumentException")
    void update_submittedDeclaration_throws() {
        CustomsDeclaration existing = buildDraft();
        existing.setStatus(CustomsDeclaration.DeclarationStatus.SUBMITTED);
        when(declarationRepo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.of(existing));

        CustomsDeclaration updated = CustomsDeclaration.builder()
                .customsOffice("Marseille")
                .build();

        assertThatThrownBy(() -> service.update(declId, updated))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("brouillon");

        verify(declarationRepo, never()).save(any());
    }

    @Test
    @DisplayName("Update avec champs null → aucun champ modifié")
    void update_nullFields_noChange() {
        CustomsDeclaration existing = buildDraft();
        when(declarationRepo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.of(existing));
        when(declarationRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CustomsDeclaration updated = CustomsDeclaration.builder().build();

        service.update(declId, updated);

        assertThat(existing.getCustomsOffice()).isEqualTo("Paris CDG");
        assertThat(existing.getOriginCountry()).isEqualTo("VN");
    }

    @Test
    @DisplayName("Update déclaration introuvable → throws")
    void update_notFound_throws() {
        when(declarationRepo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(declId, CustomsDeclaration.builder().build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── getStats ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getStats → retourne tous les comptes")
    void getStats_returnsAllCounts() {
        when(declarationRepo.countByCompanyId(companyId)).thenReturn(50L);
        when(declarationRepo.countByCompanyIdAndStatus(companyId, CustomsDeclaration.DeclarationStatus.DRAFT)).thenReturn(10L);
        when(declarationRepo.countByCompanyIdAndStatus(companyId, CustomsDeclaration.DeclarationStatus.SUBMITTED)).thenReturn(15L);
        when(declarationRepo.countByCompanyIdAndStatus(companyId, CustomsDeclaration.DeclarationStatus.UNDER_REVIEW)).thenReturn(5L);
        when(declarationRepo.countByCompanyIdAndStatus(companyId, CustomsDeclaration.DeclarationStatus.CLEARED)).thenReturn(12L);
        when(declarationRepo.countByCompanyIdAndStatus(companyId, CustomsDeclaration.DeclarationStatus.RELEASED)).thenReturn(6L);
        when(declarationRepo.countByCompanyIdAndStatus(companyId, CustomsDeclaration.DeclarationStatus.REJECTED)).thenReturn(2L);

        var stats = service.getStats();

        assertThat(stats.get("total")).isEqualTo(50L);
        assertThat(stats.get("draft")).isEqualTo(10L);
        assertThat(stats.get("submitted")).isEqualTo(15L);
        assertThat(stats.get("underReview")).isEqualTo(5L);
        assertThat(stats.get("cleared")).isEqualTo(12L);
        assertThat(stats.get("released")).isEqualTo(6L);
        assertThat(stats.get("rejected")).isEqualTo(2L);
    }
}
