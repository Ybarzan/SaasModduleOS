package com.logistix.repository;

import com.logistix.model.ReceivingOrderLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReceivingOrderLineRepository extends JpaRepository<ReceivingOrderLine, UUID> {

    List<ReceivingOrderLine> findByReceivingOrderIdOrderByCreatedAtAsc(UUID receivingOrderId);

    Optional<ReceivingOrderLine> findByReceivingOrderIdAndId(UUID receivingOrderId, UUID id);

    Optional<ReceivingOrderLine> findByReceivingOrderIdAndItemId(UUID receivingOrderId, UUID itemId);

    List<ReceivingOrderLine> findByCompanyId(UUID companyId);
}
