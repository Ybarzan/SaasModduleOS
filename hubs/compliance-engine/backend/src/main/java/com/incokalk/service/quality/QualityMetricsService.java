package com.incokalk.service.quality;

import com.incokalk.model.ClientInvoice;
import com.incokalk.model.CustomsDeclaration;
import com.incokalk.repository.ClientInvoiceRepository;
import com.incokalk.repository.CustomsDeclarationRepository;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Calcule des indicateurs qualité Six Sigma (DPMO, rendement, niveau sigma)
 * à partir des données opérationnelles réelles de l'entreprise — pas de
 * valeurs simulées. Chaque caractéristique critique qualité (CTQ) rend
 * {@code null} quand le volume de données est insuffisant plutôt que
 * d'afficher un chiffre trompeur.
 *
 * <p>Note : la CTQ "précision de réception" (qui dépendait des entités de
 * réception/entrepôt) a été retirée lors de l'extraction du domaine
 * entrepôt/réception vers le hub Logistix autonome. Une réintroduction
 * future nécessiterait un appel REST vers Logistix plutôt qu'une lecture
 * locale.</p>
 *
 * <p>Note : la CTQ "livraison à l'heure" (qui dépendait de ShipmentOrderRepository)
 * a été retirée lors du retrait du domaine Transport vers fleet-hub. Une
 * réintroduction future nécessiterait un appel REST vers fleet-hub plutôt
 * qu'une lecture locale.</p>
 */
@Service
@RequiredArgsConstructor
public class QualityMetricsService {

    private final CustomsDeclarationRepository declarationRepo;
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

        Ctq customs = customsFirstPassYield(companyId);
        Ctq invoicing = invoiceTimeliness(companyId);

        List<Ctq> ctqs = List.of(customs, invoicing);

        long totalOpportunities = ctqs.stream()
                .mapToLong(c -> c.result().opportunities()).sum();
        long totalDefects = ctqs.stream()
                .mapToLong(c -> c.result().defects()).sum();

        SigmaCalculator.Result overall = SigmaCalculator.compute(totalOpportunities, totalDefects);
        return new QualityReport(ctqs, overall);
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
