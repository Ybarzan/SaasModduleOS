package com.incokalk.repository;

import com.incokalk.model.CarrierInvoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CarrierInvoiceRepository extends JpaRepository<CarrierInvoice, UUID> {
    List<CarrierInvoice> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
    Page<CarrierInvoice> findByCompanyIdOrderByCreatedAtDesc(UUID companyId, Pageable pageable);
    Optional<CarrierInvoice> findByCompanyIdAndId(UUID companyId, UUID id);
    Optional<CarrierInvoice> findByCompanyIdAndInvoiceNumber(UUID companyId, String invoiceNumber);
    long countByCompanyIdAndStatus(UUID companyId, CarrierInvoice.InvoiceStatus status);
    List<CarrierInvoice> findByCompanyIdAndStatus(UUID companyId, CarrierInvoice.InvoiceStatus status);

    @Query("SELECT COALESCE(SUM(ci.totalAmountEur), 0) FROM CarrierInvoice ci WHERE ci.company.id = :companyId AND ci.status IN :statuses")
    BigDecimal sumTotalAmountEurByCompanyIdAndStatusIn(@Param("companyId") UUID companyId, @Param("statuses") List<CarrierInvoice.InvoiceStatus> statuses);

    long countByCompanyId(UUID companyId);
}
