package com.incokalk.repository;

import com.incokalk.model.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    List<StockMovement> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    List<StockMovement> findByCompanyIdAndItemIdOrderByCreatedAtDesc(UUID companyId, UUID itemId);

    List<StockMovement> findByWarehouseIdOrderByCreatedAtDesc(UUID warehouseId);
}
