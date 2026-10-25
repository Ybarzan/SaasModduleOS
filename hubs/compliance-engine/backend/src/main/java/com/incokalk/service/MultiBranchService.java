package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.*;
import com.incokalk.repository.*;
import com.incokalk.service.integration.finance.FinanceIntegrationClient;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiBranchService {

    private final CompanyBranchRepository branchRepo;
    private final InterBranchTransferRepository transferRepo;
    private final CompanyRepository companyRepo;
    private final FinanceIntegrationClient financeIntegrationClient;
    private final Executor taskExecutor;

    @Transactional
    public CompanyBranch addBranch(UUID parentCompanyId, UUID branchCompanyId, String branchName) {
        if (branchRepo.existsByParentCompanyIdAndBranchCompanyId(parentCompanyId, branchCompanyId)) {
            throw new IllegalArgumentException("Cette société est déjà enregistrée comme filiale");
        }
        CompanyBranch branch = CompanyBranch.builder()
                .parentCompanyId(parentCompanyId)
                .branchCompanyId(branchCompanyId)
                .branchName(branchName)
                .build();
        return branchRepo.save(branch);
    }

    @Transactional
    public void removeBranch(UUID companyId, UUID branchId) {
        CompanyBranch branch = branchRepo.findByIdAndParentCompanyId(branchId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Filiale non trouvée"));
        branch.setActive(false);
        branchRepo.save(branch);
    }

    public List<CompanyBranch> getBranches() {
        UUID companyId = TenantContext.get();
        return branchRepo.findByParentCompanyIdAndIsActiveTrue(companyId);
    }

    public Optional<CompanyBranch> getParentCompany() {
        UUID companyId = TenantContext.get();
        List<CompanyBranch> branches = branchRepo.findByBranchCompanyIdAndIsActiveTrue(companyId);
        return branches.isEmpty() ? Optional.empty() : Optional.of(branches.get(0));
    }

    private List<UUID> getConsolidationCompanyIds() {
        UUID currentCompanyId = TenantContext.get();
        Set<UUID> ids = new LinkedHashSet<>();
        ids.add(currentCompanyId);
        List<CompanyBranch> asBranch = branchRepo.findByBranchCompanyIdAndIsActiveTrue(currentCompanyId);
        if (!asBranch.isEmpty()) {
            UUID parentId = asBranch.get(0).getParentCompanyId();
            ids.add(parentId);
            branchRepo.findByParentCompanyIdAndIsActiveTrue(parentId)
                    .forEach(b -> ids.add(b.getBranchCompanyId()));
        } else {
            branchRepo.findByParentCompanyIdAndIsActiveTrue(currentCompanyId)
                    .forEach(b -> ids.add(b.getBranchCompanyId()));
        }
        return new ArrayList<>(ids);
    }

    // consolidateShipments() a ete retiree avec le domaine Transport : elle
    // bouclait sur les filiales via ShipmentOrderRepository.countByCompanyId /
    // findByCompanyIdOrderByCreatedAtDesc. Une agregation inter-societes future
    // necessiterait un appel REST batch vers fleet-hub, hors scope de cette passe.

    public Map<String, Object> getConsolidatedReport() {
        List<UUID> companyIds = getConsolidationCompanyIds();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("consolidationDate", LocalDateTime.now());
        report.put("companiesCount", companyIds.size());

        Map<UUID, Company> companiesById = companyRepo.findAllById(companyIds).stream()
                .collect(Collectors.toMap(Company::getId, c -> c));
        List<Map<String, Object>> companies = companyIds.stream()
                .map(companiesById::get)
                .filter(Objects::nonNull)
                .map(c -> {
                    Map<String, Object> comp = new LinkedHashMap<>();
                    comp.put("id", c.getId());
                    comp.put("name", c.getName());
                    comp.put("slug", c.getSlug());
                    return comp;
                })
                .toList();
        report.put("companies", companies);

        // Un appel REST par filiale et par type d'agrégat (financier, carbone) vers
        // Finance : les lancer en parallèle plutôt que les enchaîner séquentiellement
        // évite qu'un rapport sur N filiales ait une latence proportionnelle à N
        // (voir AsyncConfig.taskExecutor, déjà utilisé ailleurs dans ce service).
        List<CompletableFuture<FinanceIntegrationClient.FinancialTotals>> financialFutures = companyIds.stream()
                .map(cid -> CompletableFuture.supplyAsync(() -> financialTotalsOrZero(cid), taskExecutor))
                .toList();
        List<CompletableFuture<FinanceIntegrationClient.CarbonTotals>> carbonFutures = companyIds.stream()
                .map(cid -> CompletableFuture.supplyAsync(() -> carbonTotalsOrZero(cid), taskExecutor))
                .toList();

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalMargin = BigDecimal.ZERO;
        for (CompletableFuture<FinanceIntegrationClient.FinancialTotals> future : financialFutures) {
            FinanceIntegrationClient.FinancialTotals financials = future.join();
            totalRevenue = totalRevenue.add(financials.totalRevenue());
            totalCost = totalCost.add(financials.totalCost());
            totalMargin = totalMargin.add(financials.totalMargin());
        }
        BigDecimal totalEmissions = BigDecimal.ZERO;
        for (CompletableFuture<FinanceIntegrationClient.CarbonTotals> future : carbonFutures) {
            totalEmissions = totalEmissions.add(future.join().co2EmissionsKg());
        }
        report.put("totalRevenue", totalRevenue);
        report.put("totalCost", totalCost);
        report.put("totalMargin", totalMargin);
        report.put("totalCo2EmissionsKg", totalEmissions);

        UUID currentId = TenantContext.get();
        long activeBranches = branchRepo.countByParentCompanyId(currentId);
        report.put("activeBranches", activeBranches);

        return report;
    }

    @Transactional
    public InterBranchTransfer transferGoods(UUID companyId, UUID fromBranchId, UUID toBranchId,
                                              String goodsDescription, BigDecimal quantity) {
        CompanyBranch fromBranch = branchRepo.findByIdAndParentCompanyIdAndIsActiveTrue(fromBranchId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Filiale source non trouvée"));
        CompanyBranch toBranch = branchRepo.findByIdAndParentCompanyIdAndIsActiveTrue(toBranchId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Filiale destination non trouvée"));
        InterBranchTransfer transfer = InterBranchTransfer.builder()
                .fromBranchId(fromBranch.getId())
                .toBranchId(toBranch.getId())
                .goodsDescription(goodsDescription)
                .quantity(quantity)
                .status(InterBranchTransfer.TransferStatus.PENDING)
                .build();
        return transferRepo.save(transfer);
    }

    public List<InterBranchTransfer> getTransferHistory() {
        UUID companyId = TenantContext.get();
        List<UUID> branchIds = branchRepo.findByParentCompanyId(companyId).stream()
                .map(CompanyBranch::getId)
                .toList();
        if (branchIds.isEmpty()) {
            return List.of();
        }
        return transferRepo.findByFromBranchIdInOrToBranchIdInOrderByCreatedAtDesc(branchIds, branchIds);
    }

    /** Dégradation gracieuse : une filiale sans clé Finance configurée (ou
     * Finance indisponible) contribue zéro à la consolidation plutôt que de
     * faire échouer le rapport entier -- même principe que
     * CrossHubDashboardService côté Praxio. */
    private FinanceIntegrationClient.FinancialTotals financialTotalsOrZero(UUID companyId) {
        try {
            return financeIntegrationClient.getFinancialTotals(companyId);
        } catch (FinanceIntegrationClient.FinanceIntegrationException e) {
            log.debug("[Finance] Agrégats financiers indisponibles pour {} : {}", companyId, e.getMessage());
            return new FinanceIntegrationClient.FinancialTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    private FinanceIntegrationClient.CarbonTotals carbonTotalsOrZero(UUID companyId) {
        try {
            return financeIntegrationClient.getCarbonTotals(companyId);
        } catch (FinanceIntegrationClient.FinanceIntegrationException e) {
            log.debug("[Finance] Agrégats carbone indisponibles pour {} : {}", companyId, e.getMessage());
            return new FinanceIntegrationClient.CarbonTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }
}
