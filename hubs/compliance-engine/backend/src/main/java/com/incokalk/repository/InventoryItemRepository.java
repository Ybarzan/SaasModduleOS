package com.incokalk.repository;

import com.incokalk.model.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

    List<InventoryItem> findByCompanyId(UUID companyId);

    List<InventoryItem> findByCompanyIdAndIsActiveTrue(UUID companyId);

    Optional<InventoryItem> findByCompanyIdAndId(UUID companyId, UUID id);

    List<InventoryItem> findByCompanyIdAndNameContainingIgnoreCase(UUID companyId, String name);

    List<InventoryItem> findByCompanyIdAndSkuContainingIgnoreCase(UUID companyId, String sku);

    Optional<InventoryItem> findFirstByCompanyIdAndSku(UUID companyId, String sku);
}
