package com.incokalk.repository;

import com.incokalk.model.InvoiceFinancing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceFinancingRepository extends JpaRepository<InvoiceFinancing, UUID> {

    List<InvoiceFinancing> findByCompanyIdOrderByRequestedAtDesc(UUID companyId);

    Optional<InvoiceFinancing> findByCompanyIdAndId(UUID companyId, UUID id);

    List<InvoiceFinancing> findByCompanyIdAndStatus(UUID companyId, InvoiceFinancing.Status status);

    long countByCompanyIdAndStatus(UUID companyId, InvoiceFinancing.Status status);

    @Query("SELECT COALESCE(SUM(if.financeAmount), 0) FROM InvoiceFinancing if WHERE if.companyId = :companyId AND if.status IN :statuses")
    BigDecimal sumFinanceAmountByCompanyIdAndStatusIn(@Param("companyId") UUID companyId, @Param("statuses") List<InvoiceFinancing.Status> statuses);

    @Query("SELECT COALESCE(AVG(if.feePercent), 0) FROM InvoiceFinancing if WHERE if.companyId = :companyId")
    BigDecimal avgFeePercentByCompanyId(@Param("companyId") UUID companyId);

    Optional<InvoiceFinancing> findByInvoiceIdAndCompanyId(UUID invoiceId, UUID companyId);
}
