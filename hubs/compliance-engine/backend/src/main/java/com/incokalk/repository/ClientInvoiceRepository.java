package com.incokalk.repository;

import com.incokalk.model.ClientInvoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientInvoiceRepository extends JpaRepository<ClientInvoice, UUID> {

    List<ClientInvoice> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<ClientInvoice> findByCompanyIdAndId(UUID companyId, UUID id);

    long countByCompanyIdAndStatus(UUID companyId, ClientInvoice.InvoiceStatus status);

    List<ClientInvoice> findByCompanyIdAndStatus(UUID companyId, ClientInvoice.InvoiceStatus status);

    @Query("SELECT COALESCE(SUM(ci.totalAmount), 0) FROM ClientInvoice ci WHERE ci.company.id = :companyId AND ci.status IN :statuses")
    BigDecimal sumTotalAmountByCompanyIdAndStatusIn(@Param("companyId") UUID companyId, @Param("statuses") List<ClientInvoice.InvoiceStatus> statuses);

    @Query("SELECT COALESCE(SUM(ci.amountPaid), 0) FROM ClientInvoice ci WHERE ci.company.id = :companyId")
    BigDecimal sumAmountPaidByCompanyId(@Param("companyId") UUID companyId);

    List<ClientInvoice> findByCompanyIdAndDueDateBeforeAndStatusNotIn(UUID companyId, LocalDate date, List<ClientInvoice.InvoiceStatus> statuses);

    long countByCompanyId(UUID companyId);
}
