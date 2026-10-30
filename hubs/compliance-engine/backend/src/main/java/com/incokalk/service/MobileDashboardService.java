package com.incokalk.service;

import com.incokalk.model.ApprovalRequest;
import com.incokalk.model.Company;
import com.incokalk.model.User;
import com.incokalk.repository.ApprovalRequestRepository;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.UserRepository;
import com.incokalk.service.integration.finance.FinanceIntegrationClient;
import com.incokalk.service.integration.fleethub.FleetHubIntegrationClient;
import com.incokalk.service.integration.fleethub.RecentShipmentDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * getRecentActivity et les stats d'expedition de getDashboard (total_shipments/
 * total_cost_month) restent retirees avec le domaine Transport -- elles lisaient
 * ShipmentOrderRepository directement (requetes JPQL brutes). pending_approvals
 * reste local (ApprovalRequestRepository) ; carbon_offset_month vient desormais
 * de hubs/finance via FinanceIntegrationClient (degradation gracieuse si
 * l'integration n'est pas configuree) ; getUserProfile/getCompanyProfile sont
 * inchangees.
 * <p>
 * active_shipments restaure 2026-10-29 via FleetHubIntegrationClient.getDashboardSummary()
 * (meme endpoint cross-hub deja utilise par MultiBranchService), meme degradation
 * gracieuse. total_shipments/total_cost_month restent non restaures : aucun
 * endpoint fleet-hub existant n'expose un compte total (tous statuts) ni un cout
 * mensuel agrege -- en construire un serait hors scope de cette passe, pas devine.
 * <p>
 * getRecentShipments restaure 2026-10-30 via FleetHubIntegrationClient.listRecentShipments()
 * (nouvel endpoint de liste fleet-hub, jusqu'à 50 expéditions). Contrairement à
 * carbon_offset_month/active_shipments, PAS de dégradation gracieuse ici : un échec
 * se propage (500), pour que l'app mobile distingue "aucune expédition" (liste vide,
 * légitime) de "fleet-hub indisponible" (erreur) -- une liste vide silencieuse sur
 * panne laisserait croire à l'utilisateur qu'il n'a aucune expédition.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MobileDashboardService {

    private final ApprovalRequestRepository approvalRequestRepo;
    private final FinanceIntegrationClient financeIntegrationClient;
    private final FleetHubIntegrationClient fleetHubIntegrationClient;
    private final UserRepository userRepo;
    private final CompanyRepository companyRepo;

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboard(UUID companyId) {
        Map<String, Object> result = new LinkedHashMap<>();

        long pendingApprovals = approvalRequestRepo.countByCompanyIdAndStatus(
                companyId, ApprovalRequest.ApprovalStatus.PENDING);
        result.put("pending_approvals", pendingApprovals);

        BigDecimal carbonOffsetMonth = carbonTotalsOrZero(companyId).co2EmissionsKg();
        result.put("carbon_offset_month", carbonOffsetMonth != null ? carbonOffsetMonth.doubleValue() : 0.0);

        result.put("active_shipments", activeShipmentsOrZero(companyId));

        return result;
    }

    public User getUserProfile(UUID userId) {
        return userRepo.findById(userId).orElse(null);
    }

    public Company getCompanyProfile(UUID companyId) {
        return companyRepo.findById(companyId).orElse(null);
    }

    /** Voir le commentaire de classe : pas de dégradation gracieuse ici, une
     * panne fleet-hub doit se voir comme une erreur, jamais comme une liste
     * vide trompeuse. */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRecentShipments(UUID companyId) {
        return fleetHubIntegrationClient.listRecentShipments(companyId).stream()
                .map(this::toMobileShipmentMap)
                .toList();
    }

    private Map<String, Object> toMobileShipmentMap(RecentShipmentDto s) {
        Map<String, Object> origin = new LinkedHashMap<>();
        origin.put("country", s.getShipperCountry());
        origin.put("city", s.getShipperCity());

        Map<String, Object> destination = new LinkedHashMap<>();
        destination.put("country", s.getConsigneeCountry());
        destination.put("city", s.getConsigneeCity());

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("order_number", s.getOrderNumber());
        m.put("status", s.getStatus());
        m.put("origin", origin);
        m.put("destination", destination);
        m.put("goods_description", s.getGoodsDescription());
        m.put("weight_kg", s.getWeightKg());
        m.put("incoterm", s.getIncotermCode());
        m.put("total_cost", s.getFinalCost() != null ? s.getFinalCost() : s.getQuotedCost());
        m.put("currency", s.getCurrency());
        m.put("carrier_name", s.getCarrierName());
        m.put("transport_mode", s.getTransportMode());
        m.put("created_at", s.getCreatedAt());
        m.put("estimated_delivery", s.getEstimatedDeliveryDate());
        return m;
    }

    /** Dégradation gracieuse : une société sans clé Finance configurée (ou
     * Finance indisponible) n'empêche jamais le reste du tableau de bord
     * mobile de s'afficher -- même principe que CrossHubDashboardService
     * côté Praxio. */
    private FinanceIntegrationClient.CarbonTotals carbonTotalsOrZero(UUID companyId) {
        try {
            return financeIntegrationClient.getCarbonTotals(companyId);
        } catch (FinanceIntegrationClient.FinanceIntegrationException e) {
            log.debug("[Finance] Agrégats carbone indisponibles pour {} : {}", companyId, e.getMessage());
            return new FinanceIntegrationClient.CarbonTotals(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    /** Même dégradation gracieuse que carbonTotalsOrZero, voir ce commentaire. */
    private long activeShipmentsOrZero(UUID companyId) {
        try {
            return fleetHubIntegrationClient.getDashboardSummary(companyId).activeShipments();
        } catch (FleetHubIntegrationClient.FleetHubIntegrationException e) {
            log.debug("[FleetHub] Résumé d'activité indisponible pour {} : {}", companyId, e.getMessage());
            return 0;
        }
    }
}
