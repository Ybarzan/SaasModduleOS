package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.CompanyBranch;
import com.incokalk.model.InterBranchTransfer;
import com.incokalk.repository.*;
import com.incokalk.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("MultiBranchService — Tests unitaires")
class MultiBranchServiceTest {

    MultiBranchService service;
    CompanyBranchRepository branchRepo;
    InterBranchTransferRepository transferRepo;
    CompanyRepository companyRepo;
    ShipmentOrderRepository shipmentRepo;
    ShipmentFinancialsRepository financialsRepo;
    CarbonOffsetRepository carbonRepo;

    @BeforeEach
    void setUp() {
        branchRepo = mock(CompanyBranchRepository.class);
        transferRepo = mock(InterBranchTransferRepository.class);
        companyRepo = mock(CompanyRepository.class);
        shipmentRepo = mock(ShipmentOrderRepository.class);
        financialsRepo = mock(ShipmentFinancialsRepository.class);
        carbonRepo = mock(CarbonOffsetRepository.class);
        service = new MultiBranchService(branchRepo, transferRepo, companyRepo, shipmentRepo, financialsRepo, carbonRepo);
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

    @Test
    @DisplayName("consolidateShipments → retourne les stats agrégées")
    void consolidateShipments() {
        UUID companyId = TenantContext.get();
        when(branchRepo.findByBranchCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of());
        when(branchRepo.findByParentCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of());
        when(shipmentRepo.countByCompanyId(companyId)).thenReturn(5L);
        when(shipmentRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of());

        Map<String, Object> result = service.consolidateShipments();

        assertThat(result)
                .containsEntry("totalShipments", 5L)
                .containsEntry("companiesCount", 1);
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
