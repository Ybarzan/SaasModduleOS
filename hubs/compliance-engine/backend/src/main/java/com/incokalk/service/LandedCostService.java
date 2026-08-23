package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.LandedCost;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.LandedCostRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class LandedCostService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final LandedCostRepository landedCostRepo;
    private final CompanyRepository companyRepo;
    private final ShipmentOrderRepository shipmentRepo;
    private final CustomsDutyService customsDutyService;
    private final VatService vatService;

    public List<LandedCost> getAll() {
        UUID companyId = TenantContext.get();
        return landedCostRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    public LandedCost getById(UUID id) {
        UUID companyId = TenantContext.get();
        return landedCostRepo.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new IllegalArgumentException("Coût débarqué introuvable"));
    }

    @Transactional
    public LandedCost calculate(LandedCost cost) {
        UUID companyId = TenantContext.get();
        Company company = companyRepo.findById(companyId)
            .orElseThrow(() -> new IllegalArgumentException("Entreprise introuvable"));
        cost.setCompany(company);
        computeCosts(cost);
        return landedCostRepo.save(cost);
    }

    private void computeCosts(LandedCost cost) {
        BigDecimal cif = cost.getProductValue()
            .add(nvl(cost.getFreightCost()))
            .add(nvl(cost.getInsuranceCost()));

        CustomsDutyService.DutyResult dutyResult = customsDutyService.calculateDetailed(
            cost.getHsCode(),
            cost.getOriginCountry(),
            cost.getDestinationCountry(),
            cost.getProductValue().doubleValue(),
            nvl(cost.getFreightCost()).doubleValue(),
            nvl(cost.getInsuranceCost()).doubleValue()
        );

        cost.setDutyRate(BigDecimal.valueOf(dutyResult.dutyRate()));
        cost.setDutyAmount(BigDecimal.valueOf(dutyResult.dutyAmount()));

        VatService.VatResult vatResult = vatService.calculate(
            cost.getOriginCountry(),
            cost.getDestinationCountry(),
            cost.getProductValue().doubleValue(),
            nvl(cost.getFreightCost()).doubleValue(),
            nvl(cost.getInsuranceCost()).doubleValue(),
            cost.getIncoterm(),
            true
        );

        cost.setVatRate(BigDecimal.valueOf(vatResult.vatRate()));
        cost.setVatAmount(BigDecimal.valueOf(vatResult.vatAmount()));

        BigDecimal totalLandedCost = cif
            .add(cost.getDutyAmount())
            .add(cost.getVatAmount())
            .add(nvl(cost.getPortCharges()))
            .add(nvl(cost.getCustomsFees()))
            .add(nvl(cost.getHandlingFees()))
            .add(nvl(cost.getLastMileCost()));

        cost.setTotalLandedCost(totalLandedCost);

        int units = cost.getUnitCount() != null && cost.getUnitCount() > 0 ? cost.getUnitCount() : 1;
        cost.setUnitCount(units);
        cost.setTotalLandedCostPerUnit(totalLandedCost.divide(BigDecimal.valueOf(units), 2, RoundingMode.HALF_UP));

        if (cost.getSellingPrice() != null && cost.getSellingPrice().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal totalCost = cost.getTotalLandedCostPerUnit().multiply(BigDecimal.valueOf(units));
            BigDecimal margin = cost.getSellingPrice().subtract(totalCost);
            cost.setMargin(margin);
            if (cost.getSellingPrice().compareTo(BigDecimal.ZERO) > 0) {
                cost.setMarginPercent(margin.multiply(BigDecimal.valueOf(100))
                    .divide(cost.getSellingPrice(), 2, RoundingMode.HALF_UP));
            }
        }
    }

    @Transactional
    public LandedCost update(UUID id, LandedCost cost) {
        UUID companyId = TenantContext.get();
        LandedCost existing = landedCostRepo.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new IllegalArgumentException("Coût débarqué introuvable"));

        existing.setCalculationName(cost.getCalculationName());
        existing.setOriginCountry(cost.getOriginCountry());
        existing.setDestinationCountry(cost.getDestinationCountry());
        existing.setIncoterm(cost.getIncoterm());
        existing.setHsCode(cost.getHsCode());
        existing.setTransportMode(cost.getTransportMode());
        existing.setProductValue(cost.getProductValue());
        existing.setCurrency(cost.getCurrency());
        existing.setFreightCost(cost.getFreightCost());
        existing.setInsuranceCost(cost.getInsuranceCost());
        existing.setPortCharges(cost.getPortCharges());
        existing.setCustomsFees(cost.getCustomsFees());
        existing.setHandlingFees(cost.getHandlingFees());
        existing.setLastMileCost(cost.getLastMileCost());
        existing.setUnitCount(cost.getUnitCount());
        existing.setSellingPrice(cost.getSellingPrice());
        existing.setNotes(cost.getNotes());

        return calculate(existing);
    }

    @Transactional
    public void delete(UUID id) {
        UUID companyId = TenantContext.get();
        LandedCost existing = landedCostRepo.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new IllegalArgumentException("Coût débarqué introuvable"));
        landedCostRepo.delete(existing);
        log.info("LandedCost {} supprimée pour company {}", id, companyId);
    }

    @Transactional
    public LandedCost createFromShipment(UUID shipmentId) {
        UUID companyId = TenantContext.get();
        Company company = companyRepo.findById(companyId)
            .orElseThrow(() -> new IllegalArgumentException("Entreprise introuvable"));

        ShipmentOrder shipment = shipmentRepo.findById(shipmentId)
            .orElseThrow(() -> new IllegalArgumentException("Ordre de shipment introuvable"));

        LandedCost cost = LandedCost.builder()
            .company(company)
            .shipment(shipment)
            .calculationName("Depuis " + shipment.getOrderNumber())
            .originCountry(shipment.getShipperCountry())
            .destinationCountry(shipment.getConsigneeCountry())
            .incoterm(shipment.getIncotermCode() != null ? shipment.getIncotermCode() : "FOB")
            .productValue(shipment.getGoodsValue() != null ? BigDecimal.valueOf(shipment.getGoodsValue()) : BigDecimal.ZERO)
            .currency(shipment.getCurrency() != null ? shipment.getCurrency() : "EUR")
            .unitCount(shipment.getPackagesCount() != null ? shipment.getPackagesCount() : 1)
            .build();

        return calculate(cost);
    }

    public LandedCost whatIf(LandedCost scenario) {
        computeCosts(scenario);
        return scenario;
    }

    public List<Map<String, Object>> compareScenarios(List<LandedCost> scenarios) {
        return scenarios.stream()
            .map(s -> {
                computeCosts(s);
                Map<String, Object> map = new HashMap<>();
                map.put("originCountry", s.getOriginCountry());
                map.put("destinationCountry", s.getDestinationCountry());
                map.put("incoterm", s.getIncoterm());
                map.put("hsCode", s.getHsCode());
                map.put("transportMode", s.getTransportMode());
                map.put("productValue", s.getProductValue());
                map.put("freightCost", s.getFreightCost());
                map.put("insuranceCost", s.getInsuranceCost());
                map.put("dutyAmount", s.getDutyAmount());
                map.put("dutyRate", s.getDutyRate());
                map.put("vatAmount", s.getVatAmount());
                map.put("vatRate", s.getVatRate());
                map.put("totalLandedCost", s.getTotalLandedCost());
                map.put("totalLandedCostPerUnit", s.getTotalLandedCostPerUnit());
                return map;
            })
            .toList();
    }

    public String generateShareToken(UUID id) {
        UUID companyId = TenantContext.get();
        LandedCost existing = landedCostRepo.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new IllegalArgumentException("Coût débarqué introuvable"));
        if (existing.getShareToken() == null) {
            byte[] bytes = new byte[32];
            SECURE_RANDOM.nextBytes(bytes);
            existing.setShareToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
            landedCostRepo.save(existing);
        }
        return existing.getShareToken();
    }

    public LandedCost getByShareToken(String token) {
        return landedCostRepo.findByShareToken(token)
            .orElseThrow(() -> new IllegalArgumentException("Lien de partage invalide ou expiré"));
    }

    public Map<String, Object> getStats() {
        UUID companyId = TenantContext.get();
        List<LandedCost> costs = landedCostRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("total", costs.size());

        if (costs.isEmpty()) {
            stats.put("avgTotalLandedCost", BigDecimal.ZERO);
            stats.put("avgMargin", BigDecimal.ZERO);
            return stats;
        }

        BigDecimal sumLanded = costs.stream()
            .map(LandedCost::getTotalLandedCost)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        stats.put("avgTotalLandedCost", sumLanded.divide(BigDecimal.valueOf(costs.size()), 2, RoundingMode.HALF_UP));

        BigDecimal sumMargin = costs.stream()
            .map(LandedCost::getMargin)
            .filter(java.util.Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        long marginCount = costs.stream().filter(c -> c.getMargin() != null).count();
        stats.put("avgMargin", marginCount > 0
            ? sumMargin.divide(BigDecimal.valueOf(marginCount), 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO);

        return stats;
    }

    private BigDecimal nvl(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }
}
