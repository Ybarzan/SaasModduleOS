package com.incokalk.repository;

import com.incokalk.model.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    Optional<Invoice> findByStripeInvoiceId(String stripeInvoiceId);
    List<Invoice> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
    List<Invoice> findByCompanyIdAndStatusOrderByCreatedAtDesc(UUID companyId, Invoice.Status status);
    List<Invoice> findBySubscriptionIdOrderByCreatedAtDesc(UUID subscriptionId);
}
