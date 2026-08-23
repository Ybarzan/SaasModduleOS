package com.incokalk.repository;

import com.incokalk.model.ReceivingOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReceivingOrderRepository extends JpaRepository<ReceivingOrder, UUID> {

    List<ReceivingOrder> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    List<ReceivingOrder> findByCompanyIdAndStatusOrderByCreatedAtDesc(UUID companyId, ReceivingOrder.Status status);

    List<ReceivingOrder> findByCompanyIdAndWarehouseIdOrderByCreatedAtDesc(UUID companyId, UUID warehouseId);

    Optional<ReceivingOrder> findByCompanyIdAndId(UUID companyId, UUID id);

    Optional<ReceivingOrder> findByCompanyIdAndOrderNumber(UUID companyId, String orderNumber);

    List<ReceivingOrder> findByCompanyIdAndShipmentId(UUID companyId, UUID shipmentId);

    List<ReceivingOrder> findByCompanyIdAndShipmentIdOrderByCreatedAtDesc(UUID companyId, UUID shipmentId);
}
