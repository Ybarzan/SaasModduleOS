package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.CompanyBranch;
import com.incokalk.model.InterBranchTransfer;
import com.incokalk.repository.*;
import com.incokalk.service.integration.finance.FinanceIntegrationClient;
import com.incokalk.service.integration.fleethub.FleetHubIntegrationClient;
import com.incokalk.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("MultiBranchService — Tests unitaires")
class MultiBranchServiceTest {

    MultiBranchService service;
    CompanyBranchRepository branchRepo;
    InterBranchTransferRepository transferRepo;
    CompanyRepository companyRepo;
    FinanceIntegrationClient financeIntegrationClient;
    FleetHubIntegrationClient fleetHubIntegrationClient;

    @BeforeEach
    void setUp() {
        branchRepo = mock(CompanyBranchRepository.class);
        transferRepo = mock(InterBranchTransferRepository.class);
        companyRepo = mock(CompanyRepository.class);
        financeIntegrationClient = mock(FinanceIntegrationClient.class);
        fleetHubIntegrationClient = mock(FleetHubIntegrationClient.class);
        // Exécuteur synchrone (plutôt que le taskExecutor réel, async) : les
        // assertions sur le rapport retourné doivent rester déterministes.
        Executor synchronousExecutor = Runnable::run;
        service = new MultiBranchService(branchRepo, transferRepo, companyRepo, financeIntegrationClient,
                fleetHubIntegrationClient, synchronousExecutor);
        TenantContext.set(UUID.randomUUID());
    }

    @Test
    @DisplayName("addBranch → succès")
    void addBranch_success() {
        UUID parentId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        when(branchRepo.existsByParentCompanyIdAndBranchCompanyId(parentId, branchId)).thenReturn(false);
        when(branchRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        CompanyBranch result = service.addBranch(parentId, branchId, "Ma Filiale");

        assertThat(result.getParentCompanyId()).isEqualTo(parentId);
        assertThat(result.getBranchCompanyId()).isEqualTo(branchId);
        assertThat(result.getBranchName()).isEqualTo("Ma Filiale");
    }

    @Test
    @DisplayName("addBranch → déjà existant → exception")
    void addBranch_alreadyExists() {
        UUID parentId = UUID.randomUUID();
        UUID branchId = UUID.randomUUID();
        when(branchRepo.existsByParentCompanyIdAndBranchCompanyId(parentId, branchId)).thenReturn(true);

        assertThatThrownBy(() -> service.addBranch(parentId, branchId, "Filiale"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("déjà enregistrée");
    }

    @Test
    @DisplayName("removeBranch → désactive la filiale")
    void removeBranch() {
        UUID companyId = TenantContext.get();
        UUID branchId = UUID.randomUUID();
        CompanyBranch branch = CompanyBranch.builder().id(branchId).isActive(true).build();
        when(branchRepo.findByIdAndParentCompanyId(branchId, companyId)).thenReturn(Optional.of(branch));
        when(branchRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.removeBranch(companyId, branchId);

        assertThat(branch.isActive()).isFalse();
    }

    @Test
    @DisplayName("removeBranch → inexistant → exception")
    void removeBranch_notFound() {
        UUID companyId = TenantContext.get();
        when(branchRepo.findByIdAndParentCompanyId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeBranch(companyId, UUID.randomUUID()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("removeBranch → filiale d'une autre société → exception (pas de fuite cross-tenant)")
    void removeBranch_wrongCompany_notFound() {
        UUID companyId = TenantContext.get();
        UUID branchId = UUID.randomUUID();
        // La filiale existe mais appartient à une autre société : le repo scopé ne la retourne pas.
        when(branchRepo.findByIdAndParentCompanyId(branchId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeBranch(companyId, branchId))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(branchRepo, never()).save(any());
    }

    @Test
    @DisplayName("getBranches → retourne les filiales actives")
    void getBranches() {
        UUID companyId = TenantContext.get();
        List<CompanyBranch> branches = List.of(
                CompanyBranch.builder().branchName("Filiale 1").build()
        );
        when(branchRepo.findByParentCompanyIdAndIsActiveTrue(companyId)).thenReturn(branches);

        List<CompanyBranch> result = service.getBranches();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBranchName()).isEqualTo("Filiale 1");
    }

    // consolidateShipments() a ete retiree avec le domaine Transport
    // (ShipmentOrderRepository) -- voir MultiBranchService.

    @Test
    @DisplayName("getConsolidatedReport → agrège les totaux Finance de la société courante")
    void getConsolidatedReport_aggregatesFinanceTotals() {
        UUID companyId = TenantContext.get();
        when(branchRepo.findByBranchCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of());
        when(branchRepo.findByParentCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of());
        when(branchRepo.countByParentCompanyId(companyId)).thenReturn(0L);
        when(companyRepo.findAllById(List.of(companyId))).thenReturn(List.of(
                com.incokalk.model.Company.builder().id(companyId).name("Ma Société").slug("ma-societe").build()));
        when(financeIntegrationClient.getFinancialTotals(companyId)).thenReturn(
                new FinanceIntegrationClient.FinancialTotals(new BigDecimal("10000"), new BigDecimal("6000"), new BigDecimal("4000")));
        when(financeIntegrationClient.getCarbonTotals(companyId)).thenReturn(
                new FinanceIntegrationClient.CarbonTotals(new BigDecimal("50.5"), BigDecimal.ZERO, BigDecimal.ZERO));
        when(fleetHubIntegrationClient.getDashboardSummary(companyId)).thenReturn(
                new FleetHubIntegrationClient.FleetHubDashboardSummary(7, 3, 2));

        Map<String, Object> report = service.getConsolidatedReport();

        assertThat(report.get("totalRevenue")).isEqualTo(new BigDecimal("10000"));
        assertThat(report.get("totalMargin")).isEqualTo(new BigDecimal("4000"));
        assertThat(report.get("totalCo2EmissionsKg")).isEqualTo(new BigDecimal("50.5"));
        assertThat(report.get("totalActiveShipments")).isEqualTo(7L);
    }

    @Test
    @DisplayName("getConsolidatedReport → Finance non configurée → dégradation gracieuse (zéro, pas d'exception)")
    void getConsolidatedReport_financeUnavailable_gracefulDegradation() {
        UUID companyId = TenantContext.get();
        when(branchRepo.findByBranchCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of());
        when(branchRepo.findByParentCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of());
        when(branchRepo.countByParentCompanyId(companyId)).thenReturn(0L);
        when(companyRepo.findAllById(List.of(companyId))).thenReturn(List.of());
        when(financeIntegrationClient.getFinancialTotals(companyId))
                .thenThrow(new FinanceIntegrationClient.FinanceIntegrationException("non configurée"));
        when(financeIntegrationClient.getCarbonTotals(companyId))
                .thenThrow(new FinanceIntegrationClient.FinanceIntegrationException("non configurée"));
        when(fleetHubIntegrationClient.getDashboardSummary(companyId))
                .thenThrow(new FleetHubIntegrationClient.FleetHubIntegrationException("non configurée"));

        Map<String, Object> report = service.getConsolidatedReport();

        assertThat(report.get("totalRevenue")).isEqualTo(BigDecimal.ZERO);
        assertThat(report.get("totalCo2EmissionsKg")).isEqualTo(BigDecimal.ZERO);
        assertThat(report.get("totalActiveShipments")).isEqualTo(0L);
    }

    @Test
    @DisplayName("getConsolidatedReport → agrège les expéditions actives fleet-hub de plusieurs filiales")
    void getConsolidatedReport_aggregatesFleetHubShipmentsAcrossBranches() {
        UUID parentId = TenantContext.get();
        UUID branchId = UUID.randomUUID();
        when(branchRepo.findByBranchCompanyIdAndIsActiveTrue(parentId)).thenReturn(List.of());
        when(branchRepo.findByParentCompanyIdAndIsActiveTrue(parentId)).thenReturn(List.of(
                CompanyBranch.builder().parentCompanyId(parentId).branchCompanyId(branchId).build()));
        when(branchRepo.countByParentCompanyId(parentId)).thenReturn(1L);
        when(companyRepo.findAllById(any())).thenReturn(List.of());
        stubZeroFinance(parentId);
        stubZeroFinance(branchId);
        when(fleetHubIntegrationClient.getDashboardSummary(parentId)).thenReturn(
                new FleetHubIntegrationClient.FleetHubDashboardSummary(4, 0, 0));
        when(fleetHubIntegrationClient.getDashboardSummary(branchId)).thenReturn(
                new FleetHubIntegrationClient.FleetHubDashboardSummary(9, 0, 0));

        Map<String, Object> report = service.getConsolidatedReport();

        assertThat(report.get("totalActiveShipments")).isEqualTo(13L);
    }

    @Test
    @DisplayName("getConsolidatedReport → fleet-hub indisponible pour une filiale → dégradation gracieuse (zéro pour celle-là, pas d'exception)")
    void getConsolidatedReport_fleetHubUnavailableForOneBranch_gracefulDegradation() {
        UUID parentId = TenantContext.get();
        UUID branchId = UUID.randomUUID();
        when(branchRepo.findByBranchCompanyIdAndIsActiveTrue(parentId)).thenReturn(List.of());
        when(branchRepo.findByParentCompanyIdAndIsActiveTrue(parentId)).thenReturn(List.of(
                CompanyBranch.builder().parentCompanyId(parentId).branchCompanyId(branchId).build()));
        when(branchRepo.countByParentCompanyId(parentId)).thenReturn(1L);
        when(companyRepo.findAllById(any())).thenReturn(List.of());
        stubZeroFinance(parentId);
        stubZeroFinance(branchId);
        when(fleetHubIntegrationClient.getDashboardSummary(parentId)).thenReturn(
                new FleetHubIntegrationClient.FleetHubDashboardSummary(4, 0, 0));
        when(fleetHubIntegrationClient.getDashboardSummary(branchId))
                .thenThrow(new FleetHubIntegrationClient.FleetHubIntegrationException("intégration non configurée"));

        Map<String, Object> report = service.getConsolidatedReport();

        assertThat(report.get("totalActiveShipments")).isEqualTo(4L);
    }

    private void stubZeroFinance(UUID companyId) {
        when(financeIntegrationClient.getFinancialTotals(companyId)).thenReturn(
                new FinanceIntegrationClient.FinancialTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
        when(financeIntegrationClient.getCarbonTotals(companyId)).thenReturn(
                new FinanceIntegrationClient.CarbonTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO));
    }

    @Test
    @DisplayName("transferGoods → crée un transfert PENDING quand les deux filiales appartiennent à la société")
    void transferGoods() {
        UUID companyId = TenantContext.get();
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        CompanyBranch fromBranch = CompanyBranch.builder().id(fromId).parentCompanyId(companyId).isActive(true).build();
        CompanyBranch toBranch = CompanyBranch.builder().id(toId).parentCompanyId(companyId).isActive(true).build();
        when(branchRepo.findByIdAndParentCompanyIdAndIsActiveTrue(fromId, companyId)).thenReturn(Optional.of(fromBranch));
        when(branchRepo.findByIdAndParentCompanyIdAndIsActiveTrue(toId, companyId)).thenReturn(Optional.of(toBranch));
        when(transferRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        InterBranchTransfer transfer = service.transferGoods(companyId, fromId, toId, "Marchandise", BigDecimal.TEN);

        assertThat(transfer.getFromBranchId()).isEqualTo(fromId);
        assertThat(transfer.getToBranchId()).isEqualTo(toId);
        assertThat(transfer.getStatus()).isEqualTo(InterBranchTransfer.TransferStatus.PENDING);
        assertThat(transfer.getQuantity()).isEqualByComparingTo(BigDecimal.TEN);
    }

    @Test
    @DisplayName("transferGoods → filiale source d'une autre société → exception (pas de fuite cross-tenant)")
    void transferGoods_fromBranchWrongCompany_throws() {
        UUID companyId = TenantContext.get();
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        // fromBranchId appartient à une autre société : le repo scopé ne le retourne pas.
        when(branchRepo.findByIdAndParentCompanyIdAndIsActiveTrue(fromId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transferGoods(companyId, fromId, toId, "Marchandise", BigDecimal.TEN))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(transferRepo, never()).save(any());
    }

    @Test
    @DisplayName("transferGoods → filiale destination d'une autre société → exception (pas de fuite cross-tenant)")
    void transferGoods_toBranchWrongCompany_throws() {
        UUID companyId = TenantContext.get();
        UUID fromId = UUID.randomUUID();
        UUID toId = UUID.randomUUID();
        CompanyBranch fromBranch = CompanyBranch.builder().id(fromId).parentCompanyId(companyId).isActive(true).build();
        when(branchRepo.findByIdAndParentCompanyIdAndIsActiveTrue(fromId, companyId)).thenReturn(Optional.of(fromBranch));
        // toBranchId appartient à une autre société : le repo scopé ne le retourne pas.
        when(branchRepo.findByIdAndParentCompanyIdAndIsActiveTrue(toId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.transferGoods(companyId, fromId, toId, "Marchandise", BigDecimal.TEN))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(transferRepo, never()).save(any());
    }

    @Test
    @DisplayName("getTransferHistory → retourne les transferts des filiales de la société")
    void getTransferHistory() {
        UUID companyId = TenantContext.get();
        UUID branchId = UUID.randomUUID();
        CompanyBranch branch = CompanyBranch.builder().id(branchId).parentCompanyId(companyId).build();
        List<InterBranchTransfer> transfers = List.of(
                InterBranchTransfer.builder().goodsDescription("Test").build()
        );
        when(branchRepo.findByParentCompanyId(companyId)).thenReturn(List.of(branch));
        when(transferRepo.findByFromBranchIdInOrToBranchIdInOrderByCreatedAtDesc(List.of(branchId), List.of(branchId)))
                .thenReturn(transfers);

        List<InterBranchTransfer> result = service.getTransferHistory();

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("getTransferHistory → aucune filiale → liste vide sans appeler le repo de transferts")
    void getTransferHistory_noBranches_returnsEmpty() {
        UUID companyId = TenantContext.get();
        when(branchRepo.findByParentCompanyId(companyId)).thenReturn(List.of());

        List<InterBranchTransfer> result = service.getTransferHistory();

        assertThat(result).isEmpty();
        verify(transferRepo, never()).findByFromBranchIdInOrToBranchIdInOrderByCreatedAtDesc(any(), any());
    }
}
