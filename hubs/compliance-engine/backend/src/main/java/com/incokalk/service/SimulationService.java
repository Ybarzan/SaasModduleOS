package com.incokalk.service;

import com.incokalk.dto.shipment.PackagingRequest;
import com.incokalk.dto.shipment.PackagingResult;
import com.incokalk.dto.shipment.SimulationRequest;
import com.incokalk.dto.shipment.SimulationRequest.InsuranceLevel;
import com.incokalk.dto.shipment.SimulationResult;
import com.incokalk.dto.shipment.SimulationResult.*;
import com.incokalk.model.Incoterm;
import com.incokalk.model.Simulation;
import com.incokalk.model.User;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.SimulationRepository;
import com.incokalk.repository.UserRepository;
import com.incokalk.tenant.TenantContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SimulationService {

    private final FreightRateService freightSvc;
    private final CustomsDutyService dutySvc;
    private final CurrencyService currencySvc;
    private final SimulationRepository simRepo;
    private final UserRepository userRepo;
    private final CompanyRepository companyRepo;
    private final ObjectMapper objectMapper;
    private final ComplianceService complianceService;
    private final PackagingService packagingService;
    private final VatService vatService;

    // ── Frais fixes par Incoterm (Simulés) ───────────────────────────────────
    private static final double EXPORT_CUSTOMS = 180.0;
    private static final double ORIGIN_HANDLING = 120.0;
    private static final double ORIGIN_DOCS     = 75.0;
    private static final double DEST_HANDLING   = 250.0;
    private static final double DEST_DOCS       = 100.0;
    private static final double LAST_MILE      = 150.0;

    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public SimulationResult simulate(SimulationRequest req, UUID userId) {
        // 1. Conversion devise → EUR
        double goodsEur = currencySvc.toEur(req.getGoodsValue(), req.getCurrency());

        // 2. Estimation fret (Maintenant retourne FreightEstimate)
        FreightRateService.FreightEstimate freightEst = freightSvc.estimate(req.getOriginCountry(), req.getDestinationCountry(),
            req.getTransportMode(), req.getWeightKg(), req.getVolumeM3(), goodsEur);
        double freight = freightEst.cost();
        int estimatedDays = freightEst.days();

        // 3. Assurance
        double insurance = calcInsurance(goodsEur, freight, req.getInsuranceLevel());

        // 4. Droits de douane
        double duties = dutySvc.calculate(req.getHsCode(), req.getOriginCountry(),
            req.getDestinationCountry(), goodsEur, freight, insurance,
            req.getWeightKg() != null ? req.getWeightKg() : 0.0, null);

        // 5. TVA (import / intracommunautaire / export)
        VatService.VatResult vatResult = vatService.calculate(
            req.getOriginCountry(), req.getDestinationCountry(),
            goodsEur, freight, insurance,
            req.getIncoterm() != null ? req.getIncoterm().name() : "FOB",
            true);

        // 6. Construire la décomposition selon l'Incoterm
        SimulationResult result = buildResult(req.getIncoterm(), goodsEur, freight,
            insurance, duties, vatResult, estimatedDays, req);

        // 7. Vérification de la conformité
        result.setComplianceAlerts(complianceService.checkCompliance(req, req.getIncoterm()));

        // 8. Persister en base
        if (userId != null) {
            User user = userRepo.findById(userId).orElse(null);
            UUID companyId = TenantContext.get();
            Map<String, Object> resultMap = objectMapper.convertValue(result, Map.class);
            simRepo.save(Simulation.builder()
                .user(user)
                .company(companyId != null ? companyRepo.getReferenceById(companyId) : null)
                .incotermCode(req.getIncoterm().name())
                .originCountry(req.getOriginCountry())
                .destinationCountry(req.getDestinationCountry())
                .goodsValue(goodsEur)
                .currency(req.getCurrency())
                .transportMode(req.getTransportMode() != null ? req.getTransportMode().name() : "SEA")
                .hsCode(req.getHsCode())
                .totalBuyerCost(result.getTotalBuyerCost())
                .resultJson(resultMap)
                .build());
        }

        // 8. Comparaison tous Incoterms
        if (req.isCompareWithOthers()) {
            result.setComparison(compareAll(req, userId));
        }

        // 9. Calcul logistique (packaging + mode de transport)
        result.setLogistics(calculateLogistics(req, freight, estimatedDays));

        return result;
    }

    public List<IncotermComparison> compareAll(SimulationRequest req, UUID userId) {
        List<IncotermComparison> list = new ArrayList<>();
        for (Incoterm it : Incoterm.values()) {
            SimulationRequest r = copy(req, it);
            SimulationResult res = simulate(r, null);
            boolean compatible = !(it.mode == Incoterm.TransportMode.SEA_ONLY
                && req.getTransportMode() == SimulationRequest.TransportModeInput.AIR);
            list.add(IncotermComparison.builder()
                .code(it.name())
                .fullName(it.fullName)
                .totalBuyerCost(res.getTotalBuyerCost())
                .buyerRiskScore(res.getBuyerRiskScore())
                .riskLevel(res.getRiskLevel())
                .compatible(compatible)
                .estimatedDays(res.getEstimatedDays())
                .build());
        }
        list.sort(Comparator.comparingDouble(IncotermComparison::getTotalBuyerCost));
        return list;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Construction du résultat selon l'Incoterm
    // ─────────────────────────────────────────────────────────────────────────

    private SimulationResult buildResult(Incoterm it, double goods, double freight,
                                          double insurance, double duties, VatService.VatResult vatResult,
                                          int estimatedDays, SimulationRequest req) {
        double vat = vatResult.vatAmount();
        // Responsabilités vendeur/acheteur selon le point de transfert
        boolean sellerFreight    = Set.of(Incoterm.CFR, Incoterm.CIF, Incoterm.CPT, Incoterm.CIP,
                                           Incoterm.DAP, Incoterm.DPU, Incoterm.DDP).contains(it);
        boolean sellerInsurance  = Set.of(Incoterm.CIF, Incoterm.CIP).contains(it);
        boolean sellerExport     = it != Incoterm.EXW;
        boolean sellerOrigin     = Set.of(Incoterm.FCA, Incoterm.FAS, Incoterm.FOB, Incoterm.CFR,
                                           Incoterm.CIF, Incoterm.CPT, Incoterm.CIP, Incoterm.DAP,
                                           Incoterm.DPU, Incoterm.DDP).contains(it);
        boolean sellerDest       = Set.of(Incoterm.DAP, Incoterm.DPU, Incoterm.DDP).contains(it);
        boolean sellerDuties     = it == Incoterm.DDP;
        boolean sellerVat        = it == Incoterm.DDP;

        CostBreakdown buyer = CostBreakdown.builder()
            .goodsValue(goods)
            .exportCustoms(sellerExport ? 0 : EXPORT_CUSTOMS)
            .originHandling(sellerOrigin ? 0 : ORIGIN_HANDLING)
            .originDocumentation(sellerOrigin ? 0 : ORIGIN_DOCS)
            .freight(sellerFreight ? 0 : freight)
            .insurance(sellerInsurance ? 0 : insurance)
            .destinationHandling(sellerDest ? 0 : DEST_HANDLING)
            .destinationDocumentation(sellerDest ? 0 : DEST_DOCS)
            .importDuties(sellerDuties ? 0 : duties)
            .importVat(sellerVat ? 0 : vat)
            .lastMileDelivery(sellerDest ? 0 : LAST_MILE)
            .build();

        double total = goods + buyer.getExportCustoms() + buyer.getOriginHandling() + buyer.getOriginDocumentation()
            + buyer.getFreight() + buyer.getInsurance() + buyer.getDestinationHandling() + buyer.getDestinationDocumentation()
            + buyer.getImportDuties() + buyer.getImportVat() + buyer.getLastMileDelivery();

        List<String> warnings = new ArrayList<>();
        if (req.getHsCode() == null) warnings.add("⚠️ Sans code HS, les droits de douane sont estimés à 3.5%");
        if (it == Incoterm.EXW) warnings.add("⚠️ EXW : risque maximal pour l'acheteur — peu recommandé à l'export");
        if (it == Incoterm.DDP) warnings.add("ℹ️ DDP : vérifiez que le vendeur peut être importateur de record dans votre pays");
        if (vatResult.isExempt() || vatResult.reverseCharge()) {
            warnings.add("ℹ️ TVA : " + vatResult.notes());
        }

        List<String> recs = new ArrayList<>();
        if ("CN".equals(req.getOriginCountry()) && Set.of(Incoterm.FOB, Incoterm.FCA).contains(it))
            recs.add("✅ FOB Chine est l'Incoterm le plus utilisé sur cet axe — maîtrise totale du fret");
        if (it == Incoterm.CIF)
            recs.add("ℹ️ En CIF, l'assurance fournie par le vendeur est souvent minimale (ICC C) — vérifiez la couverture");

        return SimulationResult.builder()
            .incoterm(it.name())
            .incotermFullName(it.fullName)
            .buyerRiskScore(it.buyerRiskScore)
            .riskLevel(riskLevel(it.buyerRiskScore))
            .estimatedDays(estimatedDays)
            .buyerCosts(buyer)
            .totalBuyerCost(Math.round(total * 100.0) / 100.0)
            .responsibilities(ResponsibilityMatrix.builder()
                .sellerExportClearance(sellerExport)
                .sellerOriginCharges(sellerOrigin)
                .sellerMainFreight(sellerFreight)
                .sellerInsurance(sellerInsurance)
                .sellerDestinationCharges(sellerDest)
                .sellerImportDuties(sellerDuties)
                .sellerVat(sellerVat)
                .build())
            .recommendations(recs)
            .warnings(warnings)
            .build();
    }

    private double calcInsurance(double goods, double freight, InsuranceLevel level) {
        double rate = switch (level) {
            case MINIMUM   -> 0.002;
            case STANDARD  -> 0.005;
            case ALL_RISKS -> 0.010;
        };
        return (goods + freight) * rate;
    }

    private String riskLevel(int score) {
        return switch (score) {
            case 1 -> "LOW"; case 2 -> "LOW_MEDIUM";
            case 3 -> "MEDIUM"; case 4 -> "HIGH";
            default -> "VERY_HIGH";
        };
    }

    private SimulationResult.LogisticsInfo calculateLogistics(SimulationRequest req, double freight, int estimatedDays) {
        double weight = req.getWeightKg() != null ? req.getWeightKg() : 0;
        double volume = req.getVolumeM3() != null ? req.getVolumeM3() : 0;
        int totalBoxes = 0;
        double totalVolumeM3 = volume;
        double utilization = 0;

        if (req.getPackagingItems() != null && !req.getPackagingItems().isEmpty()) {
            PackagingRequest packReq = new PackagingRequest();
            packReq.setItems(req.getPackagingItems().stream()
                .map(pi -> {
                    PackagingRequest.PackagingItem item = new PackagingRequest.PackagingItem();
                    item.setSku(pi.getSku());
                    item.setLengthCm(pi.getLengthCm());
                    item.setWidthCm(pi.getWidthCm());
                    item.setHeightCm(pi.getHeightCm());
                    item.setWeightKg(pi.getWeightKg());
                    item.setQuantity(pi.getQuantity());
                    return item;
                })
                .collect(Collectors.toList()));
            PackagingResult packResult = packagingService.calculatePackaging(packReq);
            totalBoxes = packResult.getTotalBoxes();
            totalVolumeM3 = packResult.getTotalVolumeM3();
            utilization = packResult.getUtilizationPercent();
            weight = packResult.getTotalWeightKg();
        }

        String mode = req.getTransportMode() != null ? req.getTransportMode().name() : "SEA";
        String modeReason = "";

        if (req.getTransportMode() == null) {
            if (weight > 0 && weight < 70) {
                mode = "AIR";
                modeReason = "Poids < 70kg — express aérien recommandé";
            } else if (volume < 10 || weight < 2) {
                mode = "LTL";
                modeReason = "Volume < 10m³ ou poids < 2 tonnes — LTL (Less Than Truckload) optimal";
            } else if (volume > 25 || weight > 10) {
                mode = "FTL";
                modeReason = "Volume > 25m³ ou poids > 10 tonnes — FTL (Full Truck Load) recommandé";
            } else {
                mode = "LTL";
                modeReason = "Volume/poids intermédiaire — LTL recommandé";
            }
        }

        return SimulationResult.LogisticsInfo.builder()
            .totalBoxes(totalBoxes)
            .totalVolumeM3(Math.round(totalVolumeM3 * 1000.0) / 1000.0)
            .totalWeightKg(Math.round(weight * 100.0) / 100.0)
            .utilizationPercent(Math.round(utilization * 100.0) / 100.0)
            .recommendedMode(mode)
            .modeReason(modeReason)
            .totalPackageVolumeM3(Math.round(totalVolumeM3 * 1000.0) / 1000.0)
            .build();
    }

    @Transactional(readOnly = true)
    public List<Simulation> getUserSimulations(UUID userId) {
        return simRepo.findByUserIdOrderByCreatedAtDesc(userId, Pageable.ofSize(50))
                .getContent();
    }

    @Transactional(readOnly = true)
    public Page<Simulation> getUserSimulations(UUID userId, Pageable pageable) {
        return simRepo.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

@Transactional
public void deleteSimulation(UUID simulationId, UUID userId) {
    simRepo.findById(simulationId)
        .filter(s -> s.getUser() != null && s.getUser().getId().equals(userId))
        .ifPresent(simRepo::delete);
}

@Transactional(readOnly = true)
public Simulation getSimulation(UUID simulationId, UUID userId) {
    return simRepo.findById(simulationId)
        .filter(s -> s.getUser() != null && s.getUser().getId().equals(userId))
        .orElseThrow(() -> new com.incokalk.exception.ResourceNotFoundException(
            "Simulation introuvable: " + simulationId));
}

    private SimulationRequest copy(SimulationRequest req, Incoterm it) {
        SimulationRequest r = new SimulationRequest();
        r.setIncoterm(it);
        r.setOriginCountry(req.getOriginCountry());
        r.setDestinationCountry(req.getDestinationCountry());
        r.setGoodsValue(req.getGoodsValue());
        r.setCurrency(req.getCurrency());
        r.setTransportMode(req.getTransportMode());
        r.setInsuranceLevel(req.getInsuranceLevel());
        r.setHsCode(req.getHsCode());
        r.setWeightKg(req.getWeightKg());
        r.setVolumeM3(req.getVolumeM3());
        r.setPackagingItems(req.getPackagingItems());
        r.setCompareWithOthers(false);
        return r;
    }
}