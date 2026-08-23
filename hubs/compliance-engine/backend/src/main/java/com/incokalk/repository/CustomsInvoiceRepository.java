package com.incokalk.repository;

import com.incokalk.model.CustomsInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomsInvoiceRepository extends JpaRepository<CustomsInvoice, UUID> {

    List<CustomsInvoice> findByCompanyIdOrderByInvoiceDateDesc(UUID companyId);

    List<CustomsInvoice> findByShipmentId(UUID shipmentId);

    List<CustomsInvoice> findByCompanyIdAndShipmentId(UUID companyId, UUID shipmentId);

    Optional<CustomsInvoice> findByCompanyIdAndId(UUID companyId, UUID id);
}