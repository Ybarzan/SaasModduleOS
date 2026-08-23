package com.incokalk.service;

import com.incokalk.model.ApprovalRequest;
import com.incokalk.model.CarbonOffset;
import com.incokalk.model.Company;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.model.User;
import com.incokalk.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MobileDashboardService {

    private final ShipmentOrderRepository shipmentRepo;
    private final ApprovalRequestRepository approvalRequestRepo;
    private final CarbonOffsetRepository carbonOffsetRepo;
    private final UserRepository userRepo;
    private final CompanyRepository companyRepo;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public Map<String, Object> getDashboard(UUID companyId) {
        Map<String, Object> result = new LinkedHashMap<>();

        long totalShipments = shipmentRepo.countByCompanyId(companyId);
        result.put("total_shipments", totalShipments);

        long activeShipments = countActiveShipments(companyId);
        result.put("active_shipments", activeShipments);

        long pendingApprovals = approvalRequestRepo.countByCompanyIdAndStatus(
                companyId, ApprovalRequest.ApprovalStatus.PENDING);
        result.put("pending_approvals", pendingApprovals);

        Double totalCostMonth = sumCostMonth(companyId);
        result.put("total_cost_month", totalCostMonth != null ? totalCostMonth : 0.0);

        BigDecimal carbonOffsetMonth = carbonOffsetRepo.sumCo2EmissionsKgByCompanyId(companyId);
        result.put("carbon_offset_month", carbonOffsetMonth != null ? carbonOffsetMonth.doubleValue() : 0.0);

        List<Map<String, Object>> recentActivity = getRecentActivity(companyId);
        result.put("recent_activity", recentActivity);

        return result;
    }

    private long countActiveShipments(UUID companyId) {
        TypedQuery<Long> query = entityManager.createQuery(
                "SELECT COUNT(s) FROM ShipmentOrder s WHERE s.company.id = :companyId AND s.status IN :statuses",
                Long.class);
        query.setParameter("companyId", companyId);
        query.setParameter("statuses", List.of(ShipmentOrder.Status.BOOKED, ShipmentOrder.Status.IN_TRANSIT));
        Long result = query.getSingleResult();
        return result != null ? result : 0L;
    }

    private Double sumCostMonth(UUID companyId) {
        LocalDateTime startOfMonth = LocalDateTime.now()
                .withDayOfMonth(1)
                .withHour(0).withMinute(0).withSecond(0).withNano(0);
        TypedQuery<Double> query = entityManager.createQuery(
                "SELECT SUM(COALESCE(s.finalCost, s.quotedCost, 0.0)) FROM ShipmentOrder s " +
                        "WHERE s.company.id = :companyId AND s.createdAt >= :startOfMonth",
                Double.class);
        query.setParameter("companyId", companyId);
        query.setParameter("startOfMonth", startOfMonth);
        return query.getSingleResult();
    }

    public User getUserProfile(UUID userId) {
        return userRepo.findById(userId).orElse(null);
    }

    public Company getCompanyProfile(UUID companyId) {
        return companyRepo.findById(companyId).orElse(null);
    }

    public List<Map<String, Object>> getRecentShipments(UUID companyId, int limit) {
        List<ShipmentOrder> shipments = shipmentRepo
            .findByCompanyIdOrderByCreatedAtDesc(companyId, PageRequest.of(0, limit))
            .getContent();
        return shipments.stream().map(s -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", s.getId());
            item.put("order_number", s.getOrderNumber());
            item.put("status", s.getStatus().name());
            item.put("origin", location(s.getShipperCountry(), s.getShipperCity()));
            item.put("destination", location(s.getConsigneeCountry(), s.getConsigneeCity()));
            item.put("goods_description", s.getGoodsDescription());
            item.put("weight_kg", s.getWeightKg());
            item.put("incoterm", s.getIncotermCode());
            item.put("total_cost", s.getFinalCost() != null ? s.getFinalCost() : s.getQuotedCost());
            item.put("currency", s.getCostCurrency());
            item.put("carrier_id", s.getCarrierId());
            item.put("carrier_name", s.getCarrierName());
            item.put("transport_mode", s.getTransportMode());
            item.put("created_at", s.getCreatedAt());
            item.put("estimated_delivery", s.getEstimatedDeliveryDate());
            return item;
        }).collect(Collectors.toList());
    }

    // Map.of() rejette les valeurs nulles -- country/city peuvent etre absents
    // sur des expeditions incompletes, d'ou ce petit constructeur null-safe.
    private Map<String, Object> location(String country, String city) {
        Map<String, Object> loc = new LinkedHashMap<>();
        loc.put("country", country);
        loc.put("city", city);
        return loc;
    }

    private List<Map<String, Object>> getRecentActivity(UUID companyId) {
        List<ShipmentOrder> recent = shipmentRepo
                .findByCompanyIdOrderByCreatedAtDesc(companyId, PageRequest.of(0, 5))
                .getContent();
        return recent.stream().map(s -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", s.getId());
            item.put("order_number", s.getOrderNumber());
            item.put("status", s.getStatus().name());
            item.put("origin", s.getShipperCountry());
            item.put("destination", s.getConsigneeCountry());
            item.put("goods_description", s.getGoodsDescription());
            item.put("created_at", s.getCreatedAt());
            return item;
        }).collect(Collectors.toList());
    }
}
