package com.incokalk.repository;

import com.incokalk.model.CarrierInvoiceLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CarrierInvoiceLineRepository extends JpaRepository<CarrierInvoiceLine, UUID> {
    List<CarrierInvoiceLine> findByInvoiceId(UUID invoiceId);
    void deleteByInvoiceId(UUID invoiceId);
}
