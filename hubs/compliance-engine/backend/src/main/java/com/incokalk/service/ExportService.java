package com.incokalk.service;

import com.incokalk.model.Carrier;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.repository.CarrierRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import com.opencsv.CSVWriter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportService {

    private final ShipmentOrderRepository shipmentRepo;
    private final CarrierRepository carrierRepo;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    // ── Shipments CSV ─────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public void exportShipmentsCsv(UUID companyId, HttpServletResponse response) throws IOException {
        List<ShipmentOrder> shipments = shipmentRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=shipments_export.csv");

        try (PrintWriter writer = response.getWriter();
             CSVWriter csv = new CSVWriter(writer, ',', '"', '\\', "\n")) {

            csv.writeNext(new String[]{
                "Numéro", "Statut", "Transporteur",
                "Expéditeur", "Ville départ", "Pays départ",
                "Destinataire", "Ville arrivée", "Pays arrivée",
                "Marchandise", "Poids (kg)", "Volume (m³)",
                "Valeur", "Devise", "Incoterm",
                "Coût devisé", "Coût final", "Coût devise",
                "Date enlèvement", "Date livraison prévue", "Date livraison réelle",
                "Créé le"
            });

            for (ShipmentOrder s : shipments) {
                csv.writeNext(new String[]{
                    s.getOrderNumber(),
                    s.getStatus().name(),
                    s.getCarrier() != null ? s.getCarrier().getName() : "",
                    s.getShipperName() != null ? s.getShipperName() : "",
                    s.getShipperCity() != null ? s.getShipperCity() : "",
                    s.getShipperCountry() != null ? s.getShipperCountry() : "",
                    s.getConsigneeName() != null ? s.getConsigneeName() : "",
                    s.getConsigneeCity() != null ? s.getConsigneeCity() : "",
                    s.getConsigneeCountry() != null ? s.getConsigneeCountry() : "",
                    s.getGoodsDescription() != null ? s.getGoodsDescription() : "",
                    s.getWeightKg() != null ? String.valueOf(s.getWeightKg()) : "",
                    s.getVolumeM3() != null ? String.valueOf(s.getVolumeM3()) : "",
                    s.getGoodsValue() != null ? String.valueOf(s.getGoodsValue()) : "",
                    s.getCurrency() != null ? s.getCurrency() : "",
                    s.getIncotermCode() != null ? s.getIncotermCode() : "",
                    s.getQuotedCost() != null ? String.valueOf(s.getQuotedCost()) : "",
                    s.getFinalCost() != null ? String.valueOf(s.getFinalCost()) : "",
                    s.getCostCurrency() != null ? s.getCostCurrency() : "",
                    s.getRequestedPickupDate() != null ? s.getRequestedPickupDate().format(FMT) : "",
                    s.getEstimatedDeliveryDate() != null ? s.getEstimatedDeliveryDate().format(FMT) : "",
                    s.getActualDeliveryDate() != null ? s.getActualDeliveryDate().format(FMT) : "",
                    s.getCreatedAt() != null ? s.getCreatedAt().format(FMT) : ""
                });
            }
        }

        log.info("Export CSV shipments: {} lignes pour company {}", shipments.size(), companyId);
    }

    // ── Carriers CSV ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public void exportCarriersCsv(UUID companyId, HttpServletResponse response) throws IOException {
        List<Carrier> carriers = carrierRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);

        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=carriers_export.csv");

        try (PrintWriter writer = response.getWriter();
             CSVWriter csv = new CSVWriter(writer, ',', '"', '\\', "\n")) {

            csv.writeNext(new String[]{
                "Nom", "Code", "Modes transport",
                "Contact", "Email", "Téléphone", "Pays",
                "Actif", "Créé le"
            });

            for (Carrier c : carriers) {
                csv.writeNext(new String[]{
                    c.getName(),
                    c.getCode(),
                    c.getTransportModes() != null ? c.getTransportModes() : "",
                    c.getContactName() != null ? c.getContactName() : "",
                    c.getContactEmail() != null ? c.getContactEmail() : "",
                    c.getContactPhone() != null ? c.getContactPhone() : "",
                    c.getCountry() != null ? c.getCountry() : "",
                    c.isActive() ? "Oui" : "Non",
                    c.getCreatedAt() != null ? c.getCreatedAt().format(FMT) : ""
                });
            }
        }

        log.info("Export CSV carriers: {} lignes pour company {}", carriers.size(), companyId);
    }
}
