package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.CarbonOffset;
import com.incokalk.model.CarbonOffset.OffsetStatus;
import com.incokalk.repository.CarbonOffsetRepository;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CarbonOffsetService {

    private final CarbonOffsetRepository carbonOffsetRepo;
    private final CompanyRepository companyRepo;

    public List<CarbonOffset> getAll() {
        UUID companyId = TenantContext.get();
        return carbonOffsetRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    public CarbonOffset getById(UUID id) {
        UUID companyId = TenantContext.get();
        return carbonOffsetRepo.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Suivi carbone non trouvé"));
    }

    @Transactional
    public CarbonOffset create(CarbonOffset offset) {
        UUID companyId = TenantContext.get();
        offset.setCompany(companyRepo.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée")));
        return carbonOffsetRepo.save(offset);
    }

    @Transactional
    public CarbonOffset update(UUID id, CarbonOffset offset) {
        UUID companyId = TenantContext.get();
        CarbonOffset existing = carbonOffsetRepo.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Suivi carbone non trouvé"));

        existing.setCo2EmissionsKg(offset.getCo2EmissionsKg());
        existing.setOffsetCreditsPurchased(offset.getOffsetCreditsPurchased());
        existing.setOffsetCreditsRetired(offset.getOffsetCreditsRetired());
        existing.setOffsetProvider(offset.getOffsetProvider());
        existing.setOffsetProjectName(offset.getOffsetProjectName());
        existing.setOffsetProjectType(offset.getOffsetProjectType());
        existing.setOffsetCostPerTon(offset.getOffsetCostPerTon());
        existing.setOffsetTotalCost(offset.getOffsetTotalCost());
        existing.setOffsetCurrency(offset.getOffsetCurrency());
        existing.setCertificationId(offset.getCertificationId());
        existing.setRetiredAt(offset.getRetiredAt());
        existing.setStatus(offset.getStatus());
        existing.setNotes(offset.getNotes());
        existing.setShipment(offset.getShipment());

        return carbonOffsetRepo.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        UUID companyId = TenantContext.get();
        CarbonOffset offset = carbonOffsetRepo.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Suivi carbone non trouvé"));
        carbonOffsetRepo.delete(offset);
    }

    public Map<String, Object> getStats() {
        UUID companyId = TenantContext.get();
        Map<String, Object> stats = new LinkedHashMap<>();

        BigDecimal totalEmissions = carbonOffsetRepo.sumCo2EmissionsKgByCompanyId(companyId);
        BigDecimal totalOffset = carbonOffsetRepo.sumOffsetCreditsRetiredByCompanyId(companyId);
        BigDecimal totalCost = carbonOffsetRepo.sumOffsetTotalCostByCompanyId(companyId);

        totalEmissions = totalEmissions != null ? totalEmissions : BigDecimal.ZERO;
        totalOffset = totalOffset != null ? totalOffset : BigDecimal.ZERO;
        totalCost = totalCost != null ? totalCost : BigDecimal.ZERO;

        BigDecimal netEmissions = totalEmissions.subtract(totalOffset);
        BigDecimal offsetPercent = totalEmissions.compareTo(BigDecimal.ZERO) > 0
                ? totalOffset.divide(totalEmissions, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        stats.put("totalEmissions", totalEmissions);
        stats.put("totalOffset", totalOffset);
        stats.put("netEmissions", netEmissions);
        stats.put("offsetPercent", offsetPercent);
        stats.put("totalCost", totalCost);

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (OffsetStatus s : OffsetStatus.values()) {
            byStatus.put(s.name(), carbonOffsetRepo.countByCompanyIdAndStatus(companyId, s));
        }
        stats.put("byStatus", byStatus);

        return stats;
    }

    public Map<String, Object> getDashboard() {
        UUID companyId = TenantContext.get();
        Map<String, Object> dashboard = new LinkedHashMap<>();

        dashboard.put("stats", getStats());

        List<CarbonOffset> recent = carbonOffsetRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
        if (recent.size() > 5) {
            recent = recent.subList(0, 5);
        }
        dashboard.put("recentOffsets", recent);

        Map<String, Object> costAnalysis = new LinkedHashMap<>();
        BigDecimal totalCost = carbonOffsetRepo.sumOffsetTotalCostByCompanyId(companyId);
        costAnalysis.put("totalCost", totalCost != null ? totalCost : BigDecimal.ZERO);

        BigDecimal totalOffset = carbonOffsetRepo.sumOffsetCreditsRetiredByCompanyId(companyId);
        totalOffset = totalOffset != null ? totalOffset : BigDecimal.ZERO;
        costAnalysis.put("costPerTonne", totalOffset.compareTo(BigDecimal.ZERO) > 0
                ? (totalCost != null ? totalCost : BigDecimal.ZERO).divide(totalOffset, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
        dashboard.put("costAnalysis", costAnalysis);

        return dashboard;
    }
}
