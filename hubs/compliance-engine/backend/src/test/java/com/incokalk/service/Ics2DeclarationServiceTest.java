package com.incokalk.service;

import com.incokalk.model.Ics2Declaration;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.Ics2DeclarationRepository;
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

@DisplayName("Ics2DeclarationService — Tests update + getStats")
class Ics2DeclarationServiceTest {

    @Mock Ics2DeclarationRepository ics2Repo;
    @Mock CompanyRepository companyRepo;
    Ics2DeclarationService service;

    private UUID companyId;
    private UUID declId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        declId = UUID.randomUUID();
        TenantContext.set(companyId);
        service = new Ics2DeclarationService(ics2Repo, companyRepo, new DeclarationValidationService());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private Ics2Declaration buildDraft() {
        return Ics2Declaration.builder()
                .id(declId)
                .declarationNumber("ICS2-2026-0001")
                .status(Ics2Declaration.Ics2Status.DRAFT)
                .senderEori("FR123456789")
                .receiverEori("DE987654321")
                .vesselName("MSC Diana")
                .voyageNumber("MD2607")
                .containerNumber("MSKU1234567")
                .hsCode6("620443")
                .goodsDescription("Vêtements conteneur")
                .grossWeight(new BigDecimal("5000.00"))
                .packagesCount(20)
                .build();
    }

    // ── update ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Update DRAFT → fields copied, save called")
    void update_draftDeclaration_updatesFields() {
        Ics2Declaration existing = buildDraft();
        when(ics2Repo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.of(existing));
        when(ics2Repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Ics2Declaration updated = Ics2Declaration.builder()
                .vesselName("CMA CGM Marco Polo")
                .hsCode6("620444")
                .grossWeight(new BigDecimal("6000.00"))
                .packagesCount(25)
                .build();

        Ics2Declaration result = service.update(declId, updated);

        assertThat(result.getVesselName()).isEqualTo("CMA CGM Marco Polo");
        assertThat(result.getHsCode6()).isEqualTo("620444");
        assertThat(result.getGrossWeight()).isEqualByComparingTo(new BigDecimal("6000.00"));
        assertThat(result.getPackagesCount()).isEqualTo(25);
        assertThat(result.getSenderEori()).isEqualTo("FR123456789");
        verify(ics2Repo).save(existing);
    }

    @Test
    @DisplayName("Update non-DRAFT (SENT) → throws")
    void update_sentDeclaration_throws() {
        Ics2Declaration existing = buildDraft();
        existing.setStatus(Ics2Declaration.Ics2Status.SENT);
        when(ics2Repo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(declId, Ics2Declaration.builder().vesselName("X").build()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("brouillon");

        verify(ics2Repo, never()).save(any());
    }

    @Test
    @DisplayName("Update déclaration introuvable → throws")
    void update_notFound_throws() {
        when(ics2Repo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(declId, Ics2Declaration.builder().build()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── updateStatus ────────────────────────────────────────────────────

    @Test
    @DisplayName("DRAFT → SENT → ACCEPTED (transitions valides)")
    void updateStatus_draftToSentToAccepted() {
        Ics2Declaration existing = buildDraft();
        when(ics2Repo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.of(existing));
        when(ics2Repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Ics2Declaration result = service.updateStatus(declId, Ics2Declaration.Ics2Status.SENT);
        assertThat(result.getStatus()).isEqualTo(Ics2Declaration.Ics2Status.SENT);
        assertThat(result.getSubmittedAt()).isNotNull();

        result = service.updateStatus(declId, Ics2Declaration.Ics2Status.ACCEPTED);
        assertThat(result.getStatus()).isEqualTo(Ics2Declaration.Ics2Status.ACCEPTED);
        assertThat(result.getRespondedAt()).isNotNull();
    }

    @Test
    @DisplayName("DRAFT → ACCEPTED (transition invalide) → throws")
    void updateStatus_draftToAccepted_throws() {
        Ics2Declaration existing = buildDraft();
        when(ics2Repo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateStatus(declId, Ics2Declaration.Ics2Status.ACCEPTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalide");
    }

    @Test
    @DisplayName("ACCEPTED → * (terminal) → throws")
    void updateStatus_acceptedTerminal_throws() {
        Ics2Declaration existing = buildDraft();
        existing.setStatus(Ics2Declaration.Ics2Status.ACCEPTED);
        when(ics2Repo.findByCompanyIdAndId(companyId, declId))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.updateStatus(declId, Ics2Declaration.Ics2Status.SENT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── getStats ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getStats → retourne tous les comptes par statut")
    void getStats_returnsAllStatusCounts() {
        for (Ics2Declaration.Ics2Status status : Ics2Declaration.Ics2Status.values()) {
            when(ics2Repo.countByCompanyIdAndStatus(companyId, status))
                    .thenReturn((long) status.ordinal() + 1);
        }

        var stats = service.getStats();

        assertThat(stats).hasSize(Ics2Declaration.Ics2Status.values().length);
        assertThat(stats.get("DRAFT")).isEqualTo(1L);
        assertThat(stats.get("SENT")).isEqualTo(2L);
        assertThat(stats.get("ACCEPTED")).isEqualTo(3L);
        assertThat(stats.get("REJECTED")).isEqualTo(4L);
        assertThat(stats.get("PENDING")).isEqualTo(5L);
    }
}
