package com.incokalk.service.compliance;

import com.incokalk.model.*;
import com.incokalk.repository.*;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomsInvoiceGeneratorService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ShipmentOrderRepository shipmentRepo;
    private final ShipmentItemRepository shipmentItemRepo;
    private final TaricRateRepository taricRepo;
    private final CompanyRepository companyRepo;
    private final EoriNumberRepository eoriRepo;
    private final CustomsInvoiceRepository customsInvoiceRepo;

    @Transactional
    public CustomsInvoice generateInvoice(UUID shipmentId, UUID companyId) {
        ShipmentOrder shipment = shipmentRepo.findByIdAndCompanyId(shipmentId, companyId)
                .orElseThrow(() -> new RuntimeException("Expédition non trouvée"));

        companyRepo.findById(companyId)
                .orElseThrow(() -> new RuntimeException("Entreprise non trouvée"));

        EoriNumber eori = eoriRepo.findByCompanyIdAndIsDefaultTrue(companyId).orElse(null);

        List<ShipmentItem> items = shipmentItemRepo.findByShipmentId(shipmentId);

        CustomsInvoice invoice = CustomsInvoice.builder()
                .companyId(companyId)
                .shipmentId(shipmentId)
                .invoiceNumber(generateInvoiceNumber())
                .invoiceDate(LocalDate.now())
                .shipperName(shipment.getShipperName())
                .shipperAddress(shipment.getShipperAddress())
                .shipperCity(shipment.getShipperCity())
                .shipperCountry(shipment.getShipperCountry())
                .shipperPostalCode(shipment.getShipperPostalCode())
                .consigneeName(shipment.getConsigneeName())
                .consigneeAddress(shipment.getConsigneeAddress())
                .consigneeCity(shipment.getConsigneeCity())
                .consigneeCountry(shipment.getConsigneeCountry())
                .consigneePostalCode(shipment.getConsigneePostalCode())
                .eoriNumber(eori != null ? eori.getEori() : null)
                .goodsDescription(shipment.getGoodsDescription())
                .currency(shipment.getCurrency() != null ? shipment.getCurrency() : "EUR")
                .totalGoodsValue(BigDecimal.valueOf(shipment.getGoodsValue() != null ? shipment.getGoodsValue() : 0))
                .totalWeightKg(BigDecimal.valueOf(shipment.getWeightKg() != null ? shipment.getWeightKg() : 0))
                .totalPackages(shipment.getPackagesCount() != null ? shipment.getPackagesCount() : 0)
                .incotermCode(shipment.getIncotermCode())
                .items(new ArrayList<>())
                .createdAt(LocalDateTime.now())
                .build();

        BigDecimal totalDuty = BigDecimal.ZERO;
        BigDecimal totalVat = BigDecimal.ZERO;

        for (int i = 0; i < items.size(); i++) {
            ShipmentItem item = items.get(i);
            CustomsInvoice.InvoiceItem invoiceItem = buildInvoiceItem(item, companyId, i + 1,
                    shipment.getConsigneeCountry());
            invoiceItem.setInvoice(invoice);
            invoice.getItems().add(invoiceItem);
            totalDuty = totalDuty.add(invoiceItem.getDutyAmount());
            totalVat = totalVat.add(invoiceItem.getVatAmount());
        }

        invoice.setTotalDuty(totalDuty);
        invoice.setTotalVat(totalVat);
        invoice.setTotalAmount(invoice.getTotalGoodsValue().add(totalDuty).add(totalVat));

        invoice = customsInvoiceRepo.save(invoice);

        log.info("[CUSTOMS-INVOICE] Facture douanière {} générée pour expédition {}",
                invoice.getInvoiceNumber(), shipmentId);

        return invoice;
    }

    private CustomsInvoice.InvoiceItem buildInvoiceItem(ShipmentItem item, UUID companyId, int lineNumber,
            String destinationCountry) {
        BigDecimal dutyRate = BigDecimal.ZERO;
        String dutyType = "AD";
        boolean isPreferential = false;

        if (item.getHsCode() != null && !item.getHsCode().isBlank()) {
            List<TaricRate> rates = taricRepo.findByHsCodeAndOriginCountryAndDestinationCountry(
                    item.getHsCode(), item.getOriginCountry(), destinationCountry);
            if (!rates.isEmpty()) {
                TaricRate rate = rates.get(0);
                dutyRate = BigDecimal.valueOf(rate.getDutyRate());
                dutyType = rate.getDutyType();
                isPreferential = rate.isPrefential();
            }
        }

        BigDecimal unitPrice = item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal quantity = item.getQuantity() != null ? item.getQuantity() : BigDecimal.ONE;
        BigDecimal itemValue = unitPrice.multiply(quantity);
        BigDecimal dutyAmount = itemValue.multiply(dutyRate.divide(BigDecimal.valueOf(100)));
        BigDecimal vatAmount = dutyAmount.multiply(BigDecimal.valueOf(20));

        return CustomsInvoice.InvoiceItem.builder()
                .lineNumber(lineNumber)
                .sku(item.getSku())
                .name(item.getName())
                .description(item.getDescription())
                .hsCode(item.getHsCode())
                .quantity(item.getQuantity())
                .unit(item.getUnit() != null ? item.getUnit() : "PCS")
                .unitPrice(unitPrice)
                .totalValue(itemValue)
                .countryOfOrigin(item.getOriginCountry())
                .dutyRate(dutyRate)
                .dutyType(dutyType)
                .isPreferential(isPreferential)
                .dutyAmount(dutyAmount)
                .vatRate(BigDecimal.valueOf(20.0))
                .vatAmount(vatAmount)
                .build();
    }

    private String generateInvoiceNumber() {
        String date = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String random = String.valueOf(RANDOM.nextInt(10000));
        return "CD-" + date + "-" + random;
    }
}
