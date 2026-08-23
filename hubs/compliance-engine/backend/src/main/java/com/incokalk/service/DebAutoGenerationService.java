package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.DebDeclaration;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.DebDeclarationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DebAutoGenerationService {

    private final DebDeclarationRepository debRepository;
    private final CompanyRepository companyRepository;

    public record ShipmentData(
            UUID companyId,
            String direction,
            String countryOrigin,
            String countryDestination,
            String countryCodePartner,
            String commodityCode,
            String goodsDescription,
            BigDecimal netMassKg,
            BigDecimal statisticalValue,
            String unitOfMeasure,
            String transportMode,
            String customRegime,
            String eoriNumber,
            int reportingPeriod
    ) {}

    public record DebGenerationResult(
            String referenceNumber,
            String period,
            DebDeclaration.DebStatus status,
            String message
    ) {}

    public DebGenerationResult generateDebFromShipment(ShipmentData shipment, UUID companyId) {
        String refNumber = generateDebReference(shipment);
        DebDeclaration.DebType direction = "IMPORT".equals(shipment.direction())
                ? DebDeclaration.DebType.DEB_INTRODUCTION : DebDeclaration.DebType.DEB_EXPEDITION;

        // La DEB est toujours rattachée à l'entreprise de l'appelant (TenantContext), jamais au
        // companyId éventuellement présent dans le corps de la requête — sinon un utilisateur
        // pourrait générer une déclaration DEB attribuée à une autre entreprise.
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new IllegalArgumentException("Company not found: " + companyId));

        DebDeclaration deb = DebDeclaration.builder()
                .declarationNumber(refNumber)
                .period(String.valueOf(shipment.reportingPeriod()))
                .company(company)
                .declarationType(direction)
                .partnerCountry(shipment.countryCodePartner())
                .hsCode8(shipment.commodityCode() != null && shipment.commodityCode().length() >= 8
                        ? shipment.commodityCode().substring(0, 8) : shipment.commodityCode())
                .goodsDescription(shipment.goodsDescription())
                .netMass(shipment.netMassKg())
                .statisticalValue(shipment.statisticalValue())
                .modeOfTransport(shipment.transportMode())
                .status(DebDeclaration.DebStatus.DRAFT)
                .build();

        debRepository.save(deb);
        log.info("DEB auto-generated: ref={}, period={}, direction={}", refNumber, shipment.reportingPeriod(), shipment.direction());

        return new DebGenerationResult(refNumber,
                String.valueOf(shipment.reportingPeriod()),
                DebDeclaration.DebStatus.DRAFT,
                "DEB auto-generated from shipment data");
    }

    public DebGenerationResult generateBulkDeb(UUID companyId, List<ShipmentData> shipments, int reportingPeriod) {
        int created = 0;
        for (ShipmentData shipment : shipments) {
            if (shipment.reportingPeriod() == reportingPeriod) {
                generateDebFromShipment(shipment, companyId);
                created++;
            }
        }
        log.info("Bulk DEB generation: company={}, period={}, created={}", companyId, reportingPeriod, created);
        return new DebGenerationResult(null, String.valueOf(reportingPeriod),
                DebDeclaration.DebStatus.DRAFT, created + " DEB declarations auto-generated");
    }

    private String generateDebReference(ShipmentData shipment) {
        String dir = "IMPORT".equals(shipment.direction()) ? "IMP" : "EXP";
        String timestamp = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        return "DEB-" + dir + "-" + timestamp + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    public BigDecimal calculateStatisticalValue(BigDecimal netMassKg, BigDecimal unitValue, int quantity) {
        if (unitValue == null || quantity <= 0) return BigDecimal.ZERO;
        return unitValue.multiply(BigDecimal.valueOf(quantity))
                .setScale(2, RoundingMode.HALF_UP);
    }

    public boolean isCompulsoryDeb(BigDecimal statisticalValue, String countryCodePartner) {
        BigDecimal threshold = "GB".equals(countryCodePartner) ? new BigDecimal("1500") : new BigDecimal("25000");
        return statisticalValue != null && statisticalValue.compareTo(threshold) > 0;
    }
}
