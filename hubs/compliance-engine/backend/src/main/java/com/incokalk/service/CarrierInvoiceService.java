package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.CarrierInvoice;
import com.incokalk.model.CarrierInvoice.InvoiceStatus;
import com.incokalk.model.CarrierInvoiceLine;
import com.incokalk.repository.CarrierInvoiceLineRepository;
import com.incokalk.repository.CarrierInvoiceRepository;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CarrierInvoiceService {

    private final CarrierInvoiceRepository invoiceRepo;
    private final CarrierInvoiceLineRepository lineRepo;
    private final CompanyRepository companyRepo;

    public List<CarrierInvoice> getAll() {
        UUID companyId = TenantContext.get();
        return invoiceRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    public Page<CarrierInvoice> getAll(Pageable pageable) {
        UUID companyId = TenantContext.get();
        return invoiceRepo.findByCompanyIdOrderByCreatedAtDesc(companyId, pageable);
    }

    public CarrierInvoice getById(UUID id) {
        UUID companyId = TenantContext.get();
        CarrierInvoice invoice = invoiceRepo.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Facture transporteur non trouvée"));
        int lineCount = invoice.getLines().size(); // force-load lazy lines collection while session is open
        log.debug("Facture transporteur {} chargée avec {} lignes", id, lineCount);
        return invoice;
    }

    @Transactional
    public CarrierInvoice create(CarrierInvoice invoice) {
        UUID companyId = TenantContext.get();
        invoice.setCompany(companyRepo.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée")));

        if (invoice.getTotalAmountEur() == null) {
            BigDecimal rate = invoice.getExchangeRate() != null ? invoice.getExchangeRate() : BigDecimal.ONE;
            invoice.setTotalAmountEur(invoice.getTotalAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP));
        }

        List<CarrierInvoiceLine> lines = invoice.getLines();
        invoice.setLines(new ArrayList<>());
        CarrierInvoice saved = invoiceRepo.save(invoice);

        if (lines != null) {
            for (CarrierInvoiceLine line : lines) {
                line.setInvoice(saved);
            }
            lineRepo.saveAll(lines);
            saved.setLines(lines);
        }

        return saved;
    }

    @Transactional
    public CarrierInvoice updateStatus(UUID id, InvoiceStatus newStatus, String reason) {
        UUID companyId = TenantContext.get();
        CarrierInvoice invoice = invoiceRepo.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Facture transporteur non trouvée"));

        if (!isValidTransition(invoice.getStatus(), newStatus)) {
            throw new IllegalArgumentException("Transition de statut invalide: " + invoice.getStatus() + " → " + newStatus);
        }

        invoice.setStatus(newStatus);

        if (newStatus == InvoiceStatus.APPROVED) {
            invoice.setApprovedAt(LocalDateTime.now());
        } else if (newStatus == InvoiceStatus.PAID) {
            invoice.setPaidAt(LocalDateTime.now());
        } else if (newStatus == InvoiceStatus.DISPUTED && reason != null && !reason.isBlank()) {
            invoice.setDisputeReason(reason);
        }

        return invoiceRepo.save(invoice);
    }

    @Transactional
    public void delete(UUID id) {
        UUID companyId = TenantContext.get();
        CarrierInvoice invoice = invoiceRepo.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Facture transporteur non trouvée"));

        if (invoice.getStatus() != InvoiceStatus.DRAFT && invoice.getStatus() != InvoiceStatus.RECEIVED) {
            throw new IllegalArgumentException("Seules les factures en brouillon ou reçues peuvent être supprimées");
        }

        lineRepo.deleteByInvoiceId(id);
        invoiceRepo.delete(invoice);
    }

    public Map<String, Object> getStats() {
        UUID companyId = TenantContext.get();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", invoiceRepo.countByCompanyId(companyId));
        stats.put("received", invoiceRepo.countByCompanyIdAndStatus(companyId, InvoiceStatus.RECEIVED));
        stats.put("underReview", invoiceRepo.countByCompanyIdAndStatus(companyId, InvoiceStatus.UNDER_REVIEW));
        stats.put("approved", invoiceRepo.countByCompanyIdAndStatus(companyId, InvoiceStatus.APPROVED));
        stats.put("paid", invoiceRepo.countByCompanyIdAndStatus(companyId, InvoiceStatus.PAID));
        stats.put("disputed", invoiceRepo.countByCompanyIdAndStatus(companyId, InvoiceStatus.DISPUTED));
        stats.put("rejected", invoiceRepo.countByCompanyIdAndStatus(companyId, InvoiceStatus.REJECTED));

        BigDecimal sumEur = invoiceRepo.sumTotalAmountEurByCompanyIdAndStatusIn(
                companyId, List.of(InvoiceStatus.APPROVED, InvoiceStatus.PAID));
        stats.put("totalAmountEur", sumEur != null ? sumEur : BigDecimal.ZERO);

        return stats;
    }

    @Transactional
    public CarrierInvoice reconcile(UUID id, BigDecimal negotiatedRate, String notes) {
        UUID companyId = TenantContext.get();
        CarrierInvoice invoice = invoiceRepo.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Facture transporteur non trouvée"));

        invoice.setNegotiatedRate(negotiatedRate);
        invoice.setReconciliationNotes(notes);

        if (negotiatedRate != null && invoice.getTotalAmount() != null) {
            BigDecimal variance = invoice.getTotalAmount().subtract(negotiatedRate);
            invoice.setVariance(variance);

            if (negotiatedRate.compareTo(BigDecimal.ZERO) != 0) {
                BigDecimal percent = variance.divide(negotiatedRate, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP);
                invoice.setVariancePercent(percent);
            }
        }

        return invoiceRepo.save(invoice);
    }

    private boolean isValidTransition(InvoiceStatus current, InvoiceStatus next) {
        return switch (current) {
            case RECEIVED -> next == InvoiceStatus.UNDER_REVIEW;
            case UNDER_REVIEW -> next == InvoiceStatus.APPROVED || next == InvoiceStatus.REJECTED || next == InvoiceStatus.DISPUTED;
            case APPROVED -> next == InvoiceStatus.PAID || next == InvoiceStatus.DISPUTED;
            case DISPUTED -> next == InvoiceStatus.UNDER_REVIEW;
            case DRAFT -> next == InvoiceStatus.RECEIVED || next == InvoiceStatus.DISPUTED;
            case PAID, REJECTED -> false;
        };
    }
}
