package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.*;
import com.incokalk.repository.*;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MultiBranchService {

    private final CompanyBranchRepository branchRepo;
    private final InterBranchTransferRepository transferRepo;
    private final CompanyRepository companyRepo;
    private final ShipmentOrderRepository shipmentRepo;
    private final ShipmentFinancialsRepository financialsRepo;
    private final CarbonOffsetRepository carbonRepo;

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

    public Map<String, Object> consolidateShipments() {
        List<UUID> companyIds = getConsolidationCompanyIds();
        long totalShipments = 0;
        double totalCost = 0;
        Map<String, Long> byStatus = new HashMap<>();
        for (UUID cid : companyIds) {
            totalShipments += shipmentRepo.countByCompanyId(cid);
            List<ShipmentOrder> shipments = shipmentRepo.findByCompanyIdOrderByCreatedAtDesc(cid);
            for (ShipmentOrder s : shipments) {
                totalCost += s.getFinalCost() != null ? s.getFinalCost()
                        : (s.getQuotedCost() != null ? s.getQuotedCost() : 0);
                byStatus.merge(s.getStatus().name(), 1L, Long::sum);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalShipments", totalShipments);
        result.put("totalCost", totalCost);
        result.put("byStatus", byStatus);
        result.put("companiesCount", companyIds.size());
        return result;
    }

    public Map<String, Object> consolidateFinancials() {
        List<UUID> companyIds = getConsolidationCompanyIds();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalMargin = BigDecimal.ZERO;
        for (UUID cid : companyIds) {
            totalRevenue = totalRevenue.add(financialsRepo.sumRevenueByCompanyId(cid));
            totalCost = totalCost.add(financialsRepo.sumCostByCompanyId(cid));
            totalMargin = totalMargin.add(financialsRepo.sumMarginByCompanyId(cid));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalRevenue", totalRevenue);
        result.put("totalCost", totalCost);
        result.put("totalMargin", totalMargin);
        result.put("companiesCount", companyIds.size());
        return result;
    }

    public Map<String, Object> consolidateCarbon() {
        List<UUID> companyIds = getConsolidationCompanyIds();
        BigDecimal totalEmissions = BigDecimal.ZERO;
        BigDecimal totalCreditsRetired = BigDecimal.ZERO;
        BigDecimal totalOffsetCost = BigDecimal.ZERO;
        for (UUID cid : companyIds) {
            totalEmissions = totalEmissions.add(carbonRepo.sumCo2EmissionsKgByCompanyId(cid));
            totalCreditsRetired = totalCreditsRetired.add(carbonRepo.sumOffsetCreditsRetiredByCompanyId(cid));
            totalOffsetCost = totalOffsetCost.add(carbonRepo.sumOffsetTotalCostByCompanyId(cid));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCo2EmissionsKg", totalEmissions);
        result.put("totalOffsetCreditsRetired", totalCreditsRetired);
        result.put("totalOffsetCost", totalOffsetCost);
        result.put("companiesCount", companyIds.size());
        return result;
    }

    public Map<String, Object> getConsolidatedReport() {
        List<UUID> companyIds = getConsolidationCompanyIds();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("consolidationDate", LocalDateTime.now());
        report.put("companiesCount", companyIds.size());

        List<Map<String, Object>> companies = new ArrayList<>();
        for (UUID cid : companyIds) {
            companyRepo.findById(cid).ifPresent(c -> {
                Map<String, Object> comp = new LinkedHashMap<>();
                comp.put("id", c.getId());
                comp.put("name", c.getName());
                comp.put("slug", c.getSlug());
                companies.add(comp);
            });
        }
        report.put("companies", companies);

        long totalShipments = 0;
        for (UUID cid : companyIds) {
            totalShipments += shipmentRepo.countByCompanyId(cid);
        }
        report.put("totalShipments", totalShipments);

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCost = BigDecimal.ZERO;
        BigDecimal totalMargin = BigDecimal.ZERO;
        for (UUID cid : companyIds) {
            totalRevenue = totalRevenue.add(financialsRepo.sumRevenueByCompanyId(cid));
            totalCost = totalCost.add(financialsRepo.sumCostByCompanyId(cid));
            totalMargin = totalMargin.add(financialsRepo.sumMarginByCompanyId(cid));
        }
        report.put("totalRevenue", totalRevenue);
        report.put("totalCost", totalCost);
        report.put("totalMargin", totalMargin);

        BigDecimal totalEmissions = BigDecimal.ZERO;
        for (UUID cid : companyIds) {
            totalEmissions = totalEmissions.add(carbonRepo.sumCo2EmissionsKgByCompanyId(cid));
        }
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
}
