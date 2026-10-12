package com.incokalk.service;

import com.incokalk.dto.analytics.DashboardStatsDTO;
import com.incokalk.dto.analytics.DashboardSummaryDTO;
import com.incokalk.model.ClientInvoice;
import com.incokalk.model.CustomsDeclaration;
import com.incokalk.repository.ClientInvoiceRepository;
import com.incokalk.repository.CustomsDeclarationRepository;
import com.incokalk.repository.SimulationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * getShipmentsOverTime/getShipmentsByStatus/getCostByCarrier/getCostByMode/
 * getTopRoutes/getIncotermUsage/getWeightDistribution/getVolumeDistribution/
 * getCostTrends/getCarrierPerformance ont été retirées avec le domaine
 * Transport : chacune lisait ShipmentOrderRepository (et certaines
 * CarrierRepository) directement, sans remnant indépendant. getIncotermUsage
 * lisait {@code ShipmentOrder.getIncotermCode()} (l'Incoterm effectivement
 * enregistré sur l'expédition), pas le modèle statique {@code Incoterm} —
 * donc elle aussi couplée au domaine Transport malgré son nom.
 *
 * <p>getDashboardStats survit, réduite à ses deux seuls champs indépendants du
 * domaine Transport : totalSimulations/simulationsThisMonth (SimulationRepository).
 * Tous les autres champs du tableau de bord (expéditions, coûts, poids/volume,
 * transporteurs, CO₂) ont été retirés. Une réintroduction future de ces
 * statistiques nécessiterait un appel REST vers fleet-hub plutôt qu'une lecture
 * locale — perte fonctionnelle importante pour la page Dashboard, attendue et
 * déjà prise en compte côté frontend (tâche parallèle de refonte de cette page).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyticsService {

    private static final List<CustomsDeclaration.DeclarationStatus> OPEN_DECLARATION_STATUSES = List.of(
            CustomsDeclaration.DeclarationStatus.DRAFT,
            CustomsDeclaration.DeclarationStatus.SUBMITTED,
            CustomsDeclaration.DeclarationStatus.UNDER_REVIEW);

    private static final List<ClientInvoice.InvoiceStatus> SETTLED_INVOICE_STATUSES = List.of(
            ClientInvoice.InvoiceStatus.PAID,
            ClientInvoice.InvoiceStatus.CANCELLED);

    private final SimulationRepository simulationRepo;
    private final CustomsDeclarationRepository customsDeclarationRepo;
    private final ClientInvoiceRepository clientInvoiceRepo;

    @Transactional(readOnly = true)
    public DashboardStatsDTO getDashboardStats(UUID companyId, String period) {
        long totalSimulations = simulationRepo.countByCompanyId(companyId);
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        long simulationsThisMonth = simulationRepo.countByCompanyIdAndCreatedAtAfter(companyId, monthStart);

        return DashboardStatsDTO.builder()
                .totalSimulations(totalSimulations)
                .simulationsThisMonth(simulationsThisMonth)
                .period(period)
                .build();
    }

    /**
     * Résumé compact pour le tableau de bord cross-hub de Praxio. Appelé via
     * la clé API générale (ApiKeyService/ApiKeyAuthFilter) : le companyId
     * vient du TenantContext résolu par la clé, jamais d'un paramètre client.
     */
    @Transactional(readOnly = true)
    public DashboardSummaryDTO getDashboardSummary(UUID companyId) {
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        long simulationsThisMonth = simulationRepo.countByCompanyIdAndCreatedAtAfter(companyId, monthStart);

        long openDeclarations = customsDeclarationRepo.countByCompanyIdAndStatusIn(companyId, OPEN_DECLARATION_STATUSES);

        LocalDate today = LocalDate.now();
        long overdueInvoicesCount = clientInvoiceRepo.countByCompanyIdAndDueDateBeforeAndStatusNotIn(
                companyId, today, SETTLED_INVOICE_STATUSES);
        var overdueInvoicesAmount = clientInvoiceRepo.sumTotalAmountByCompanyIdAndDueDateBeforeAndStatusNotIn(
                companyId, today, SETTLED_INVOICE_STATUSES);

        return DashboardSummaryDTO.builder()
                .simulationsThisMonth(simulationsThisMonth)
                .openDeclarations(openDeclarations)
                .overdueInvoicesCount(overdueInvoicesCount)
                .overdueInvoicesAmount(overdueInvoicesAmount)
                .build();
    }
}
