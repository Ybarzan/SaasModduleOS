package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.LandedCost;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.LandedCostRepository;
import com.incokalk.service.integration.fleethub.FleetHubIntegrationClient;
import com.incokalk.service.integration.fleethub.ShipmentSummary;
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

    private static final java.util.regex.Pattern ISO2_COUNTRY_CODE = java.util.regex.Pattern.compile("^[A-Z]{2}$");

    private final LandedCostRepository landedCostRepo;
    private final CompanyRepository companyRepo;
    private final CustomsDutyService customsDutyService;
    private final VatService vatService;
    private final FleetHubIntegrationClient fleetHubIntegrationClient;

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

        if (!dutyResult.rateAvailable()) {
            // Le total ci-dessous exclut alors les droits : on le dit dans la fiche plutôt que
            // d'enregistrer un taux inventé (colonne non nullable → 0, explicité dans notes).
            String flag = "[Droits non inclus] " + dutyResult.notes();
            String existing = cost.getNotes();
            if (existing == null || !existing.contains("[Droits non inclus]")) {
                cost.setNotes(existing == null || existing.isBlank() ? flag : existing + "\n" + flag);
            }
        }
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

    /**
     * Préremplit un coût débarqué depuis une expédition fleet-hub, via
     * {@link FleetHubIntegrationClient#getShipmentSummary} (déjà utilisé par
     * CustomsInvoiceGeneratorService/OrchestrationExecutor). Ne sauvegarde
     * JAMAIS et n'appelle JAMAIS {@link #calculate}, volontairement : le
     * champ pays libre de fleet-hub (ShipmentOrder.shipperCountry/
     * consigneeCountry, simple texte, aucun format imposé) n'est pas la même
     * chose qu'un code ISO-2 fiable -- le calcul de droits de douane
     * (CustomsDutyService) compare ce code exactement (appartenance à l'UE,
     * recherche TARIC), et un mauvais code n'y produit pas d'erreur visible
     * mais un résultat silencieusement faux (repli sur un taux générique).
     * Mieux vaut laisser le champ vide pour que l'utilisateur le corrige
     * dans le formulaire existant que de deviner. originCountry/
     * destinationCountry ne sont donc préremplis que si la valeur libre
     * ressemble déjà à un vrai code ISO-2 (2 lettres après trim+majuscule) ;
     * hsCode seulement si l'expédition a exactement un article (ambigu
     * sinon) ; les autres champs (incoterm, devise, mode de transport,
     * valeur marchandise) ne portent pas ce risque et sont copiés tels quels
     * quand connus.
     */
    public LandedCost createFromShipment(UUID shipmentId) {
        UUID companyId = TenantContext.get();
        ShipmentSummary summary;
        try {
            summary = fleetHubIntegrationClient.getShipmentSummary(shipmentId, companyId);
        } catch (FleetHubIntegrationClient.FleetHubIntegrationException e) {
            throw new IllegalArgumentException(
                    "Impossible de récupérer cette expédition depuis fleet-hub : " + e.getMessage());
        }

        LandedCost.LandedCostBuilder builder = LandedCost.builder()
                .shipmentId(shipmentId)
                .calculationName(summary.getOrderNumber() != null
                        ? "Expédition " + summary.getOrderNumber() : null);

        String origin = normalizeIso2CountryCode(summary.getShipperCountry());
        if (origin != null) builder.originCountry(origin);
        String destination = normalizeIso2CountryCode(summary.getConsigneeCountry());
        if (destination != null) builder.destinationCountry(destination);

        if (summary.getIncotermCode() != null && !summary.getIncotermCode().isBlank()) {
            builder.incoterm(summary.getIncotermCode());
        }
        if (summary.getCurrency() != null && !summary.getCurrency().isBlank()) {
            builder.currency(summary.getCurrency());
        }
        if (summary.getTransportMode() != null && !summary.getTransportMode().isBlank()) {
            builder.transportMode(summary.getTransportMode());
        }
        if (summary.getGoodsValue() != null) {
            builder.productValue(BigDecimal.valueOf(summary.getGoodsValue()));
        }
        if (summary.getItems() != null && summary.getItems().size() == 1) {
            String hsCode = summary.getItems().get(0).getHsCode();
            if (hsCode != null && !hsCode.isBlank()) {
                builder.hsCode(hsCode);
            }
        }

        return builder.build();
    }

    /** null si la valeur n'est pas déjà sous la forme exacte d'un code ISO-2
     *  (2 lettres) -- jamais de conversion nom-de-pays -> code (voir
     *  {@link #createFromShipment}). */
    private String normalizeIso2CountryCode(String rawCountry) {
        if (rawCountry == null) return null;
        String candidate = rawCountry.trim().toUpperCase();
        return ISO2_COUNTRY_CODE.matcher(candidate).matches() ? candidate : null;
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
