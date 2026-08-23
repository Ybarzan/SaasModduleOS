package com.incokalk.service.quality;

import com.incokalk.model.ClientInvoice;
import com.incokalk.model.CustomsDeclaration;
import com.incokalk.model.Discrepancy;
import com.incokalk.model.ReceivingOrderLine;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.repository.ClientInvoiceRepository;
import com.incokalk.repository.CustomsDeclarationRepository;
import com.incokalk.repository.DiscrepancyRepository;
import com.incokalk.repository.ReceivingOrderLineRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Calcule des indicateurs qualité Six Sigma (DPMO, rendement, niveau sigma)
 * à partir des données opérationnelles réelles de l'entreprise — pas de
 * valeurs simulées. Chaque caractéristique critique qualité (CTQ) rend
 * {@code null} quand le volume de données est insuffisant plutôt que
 * d'afficher un chiffre trompeur.
 */
@Service
@RequiredArgsConstructor
public class QualityMetricsService {

    private final ShipmentOrderRepository shipmentRepo;
    private final CustomsDeclarationRepository declarationRepo;
    private final ReceivingOrderLineRepository lineRepo;
    private final DiscrepancyRepository discrepancyRepo;
    private final ClientInvoiceRepository invoiceRepo;

    private static final List<CustomsDeclaration.DeclarationStatus> SUBMITTED_DECLARATION_STATUSES = List.of(
            CustomsDeclaration.DeclarationStatus.SUBMITTED,
            CustomsDeclaration.DeclarationStatus.UNDER_REVIEW,
            CustomsDeclaration.DeclarationStatus.CLEARED,
            CustomsDeclaration.DeclarationStatus.RELEASED,
            CustomsDeclaration.DeclarationStatus.REJECTED
    );

    public record Ctq(String key, String label, String description, SigmaCalculator.Result result) {}

    public record QualityReport(List<Ctq> characteristics, SigmaCalculator.Result overall) {}

    public QualityReport getReport() {
        UUID companyId = TenantContext.get();

        Ctq onTime = onTimeDelivery(companyId);
        Ctq customs = customsFirstPassYield(companyId);
        Ctq receiving = receivingAccuracy(companyId);
        Ctq invoicing = invoiceTimeliness(companyId);

        List<Ctq> ctqs = List.of(onTime, customs, receiving, invoicing);

        long totalOpportunities = ctqs.stream()
                .mapToLong(c -> c.result().opportunities()).sum();
        long totalDefects = ctqs.stream()
                .mapToLong(c -> c.result().defects()).sum();

        SigmaCalculator.Result overall = SigmaCalculator.compute(totalOpportunities, totalDefects);
        return new QualityReport(ctqs, overall);
    }

    private Ctq onTimeDelivery(UUID companyId) {
        List<ShipmentOrder> delivered = shipmentRepo.findByCompanyIdAndStatus(companyId, ShipmentOrder.Status.DELIVERED);
        List<ShipmentOrder> measurable = delivered.stream()
                .filter(s -> s.getEstimatedDeliveryDate() != null && s.getActualDeliveryDate() != null)
                .toList();
        long opportunities = measurable.size();
        long defects = measurable.stream()
                .filter(s -> s.getActualDeliveryDate().isAfter(s.getEstimatedDeliveryDate()))
                .count();
        return new Ctq("on_time_delivery", "Livraison à l'heure",
                "Expéditions livrées à la date estimée ou avant, sur celles où les deux dates sont renseignées",
                SigmaCalculator.compute(opportunities, defects));
    }

    private Ctq customsFirstPassYield(UUID companyId) {
        long opportunities = SUBMITTED_DECLARATION_STATUSES.stream()
                .mapToLong(status -> declarationRepo.countByCompanyIdAndStatus(companyId, status))
                .sum();
        long defects = declarationRepo.countByCompanyIdAndStatus(companyId, CustomsDeclaration.DeclarationStatus.REJECTED);
        return new Ctq("customs_first_pass", "Conformité déclarations douanières",
                "Déclarations soumises acceptées du premier coup (non rejetées) par les autorités",
                SigmaCalculator.compute(opportunities, defects));
    }

    private Ctq receivingAccuracy(UUID companyId) {
        List<ReceivingOrderLine> lines = lineRepo.findByCompanyId(companyId).stream()
                .filter(l -> l.getQuantityReceived() != null && l.getQuantityReceived().compareTo(BigDecimal.ZERO) > 0)
                .toList();
        long opportunities = lines.size();

        Set<UUID> defectiveLineIds = discrepancyRepo.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .map(Discrepancy::getLineId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        long defects = lines.stream().filter(l -> defectiveLineIds.contains(l.getId())).count();

        return new Ctq("receiving_accuracy", "Précision de réception",
                "Lignes de réception sans écart (manquant, surplus, dommage ou produit imprévu)",
                SigmaCalculator.compute(opportunities, defects));
    }

    private Ctq invoiceTimeliness(UUID companyId) {
        List<ClientInvoice> issued = invoiceRepo.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .filter(i -> i.getStatus() != ClientInvoice.InvoiceStatus.DRAFT)
                .toList();
        long opportunities = issued.size();
        LocalDate today = LocalDate.now();
        long defects = issued.stream()
                .filter(i -> i.getStatus() != ClientInvoice.InvoiceStatus.PAID
                        && i.getStatus() != ClientInvoice.InvoiceStatus.CANCELLED)
                .filter(i -> i.getDueDate() != null && i.getDueDate().isBefore(today))
                .count();

        return new Ctq("invoice_timeliness", "Ponctualité de facturation",
                "Factures client émises et non en retard de paiement (hors annulées)",
                SigmaCalculator.compute(opportunities, defects));
    }
}
