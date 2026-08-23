package com.incokalk.repository;

import com.incokalk.model.ShipmentItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ShipmentItemRepository extends JpaRepository<ShipmentItem, UUID> {

    List<ShipmentItem> findByShipmentId(UUID shipmentId);

    List<ShipmentItem> findByShipmentIdAndCompanyId(UUID shipmentId, UUID companyId);

    @Modifying
    @Query("DELETE FROM ShipmentItem si WHERE si.shipmentId = :shipmentId")
    void deleteByShipmentId(UUID shipmentId);
}