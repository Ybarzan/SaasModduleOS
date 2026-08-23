package com.incokalk.repository;

import com.incokalk.model.StockBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockBalanceRepository extends JpaRepository<StockBalance, UUID> {

    Optional<StockBalance> findByWarehouseIdAndItemId(UUID warehouseId, UUID itemId);

    List<StockBalance> findByCompanyIdOrderByLastUpdatedDesc(UUID companyId);

    List<StockBalance> findByCompanyIdAndWarehouseIdOrderByLastUpdatedDesc(UUID companyId, UUID warehouseId);
}
