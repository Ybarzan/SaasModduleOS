package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.ClientInvoice;
import com.incokalk.model.ClientInvoice.InvoiceStatus;
import com.incokalk.model.PaymentTerm;
import com.incokalk.repository.ClientInvoiceRepository;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.PaymentTermRepository;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientInvoiceService {

    private final ClientInvoiceRepository clientInvoiceRepo;
    private final PaymentTermRepository paymentTermRepo;
    private final CompanyRepository companyRepo;

    public List<ClientInvoice> getAll() {
        UUID companyId = TenantContext.get();
        return clientInvoiceRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    public ClientInvoice getById(UUID id) {
        UUID companyId = TenantContext.get();
        return clientInvoiceRepo.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Facture client non trouvée"));
    }

    @Transactional
    public ClientInvoice create(ClientInvoice invoice) {
        UUID companyId = TenantContext.get();
        invoice.setCompany(companyRepo.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée")));

        if (invoice.getPaymentTerm() != null && invoice.getDueDate() == null) {
            invoice.setDueDate(invoice.getInvoiceDate().plusDays(invoice.getPaymentTerm().getDaysUntilDue()));
        }

        invoice.setBalanceDue(invoice.getTotalAmount().subtract(
                invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO));

        return clientInvoiceRepo.save(invoice);
    }

    @Transactional
    public ClientInvoice update(UUID id, ClientInvoice invoice) {
        UUID companyId = TenantContext.get();
        ClientInvoice existing = clientInvoiceRepo.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Facture client non trouvée"));

        existing.setInvoiceNumber(invoice.getInvoiceNumber());
        existing.setInvoiceDate(invoice.getInvoiceDate());
        existing.setDueDate(invoice.getDueDate());
        existing.setClient(invoice.getClient());
        existing.setPaymentTerm(invoice.getPaymentTerm());
        existing.setClientName(invoice.getClientName());
        existing.setClientEmail(invoice.getClientEmail());
        existing.setSubtotal(invoice.getSubtotal());
        existing.setVatAmount(invoice.getVatAmount());
        existing.setTotalAmount(invoice.getTotalAmount());
        existing.setCurrency(invoice.getCurrency());
        existing.setEarlyPaymentDiscountAmount(invoice.getEarlyPaymentDiscountAmount());
        existing.setEarlyPaymentDiscountDeadline(invoice.getEarlyPaymentDiscountDeadline());
        existing.setLateFeeApplied(invoice.getLateFeeApplied());
        existing.setPaymentReference(invoice.getPaymentReference());
        existing.setNotes(invoice.getNotes());

        existing.setBalanceDue(existing.getTotalAmount().subtract(
                existing.getAmountPaid() != null ? existing.getAmountPaid() : BigDecimal.ZERO));

        return clientInvoiceRepo.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        UUID companyId = TenantContext.get();
        ClientInvoice invoice = clientInvoiceRepo.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Facture client non trouvée"));

        if (invoice.getStatus() != InvoiceStatus.DRAFT) {
            throw new IllegalArgumentException("Seules les factures en brouillon peuvent être supprimées");
        }

        clientInvoiceRepo.delete(invoice);
    }

    @Transactional
    public ClientInvoice updateStatus(UUID id, InvoiceStatus newStatus) {
        UUID companyId = TenantContext.get();
        ClientInvoice invoice = clientInvoiceRepo.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Facture client non trouvée"));

        if (!isValidTransition(invoice.getStatus(), newStatus)) {
            throw new IllegalArgumentException("Transition de statut invalide: " + invoice.getStatus() + " → " + newStatus);
        }

        invoice.setStatus(newStatus);

        if (newStatus == InvoiceStatus.PAID) {
            invoice.setPaidAt(LocalDateTime.now());
            invoice.setAmountPaid(invoice.getTotalAmount());
            invoice.setBalanceDue(BigDecimal.ZERO);
        }

        return clientInvoiceRepo.save(invoice);
    }

    @Transactional
    public ClientInvoice recordPayment(UUID id, BigDecimal amount, String reference) {
        UUID companyId = TenantContext.get();
        ClientInvoice invoice = clientInvoiceRepo.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Facture client non trouvée"));

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le montant du paiement doit être positif");
        }

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new IllegalStateException("Cette facture est déjà entièrement payée");
        }
        if (invoice.getStatus() == InvoiceStatus.CANCELLED) {
            throw new IllegalStateException("Impossible d'enregistrer un paiement sur une facture annulée");
        }
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw new IllegalStateException("Impossible d'enregistrer un paiement sur une facture en brouillon : envoyez-la d'abord");
        }

        BigDecimal currentPaid = invoice.getAmountPaid() != null ? invoice.getAmountPaid() : BigDecimal.ZERO;
        BigDecimal remaining = invoice.getTotalAmount().subtract(currentPaid);
        if (amount.compareTo(remaining) > 0) {
            throw new IllegalArgumentException(
                    "Le montant du paiement (" + amount + ") dépasse le solde restant dû (" + remaining + ")");
        }

        BigDecimal newPaid = currentPaid.add(amount);
        invoice.setAmountPaid(newPaid);
        invoice.setBalanceDue(invoice.getTotalAmount().subtract(newPaid));
        invoice.setPaymentReference(reference);

        if (newPaid.compareTo(invoice.getTotalAmount()) >= 0) {
            invoice.setStatus(InvoiceStatus.PAID);
            invoice.setPaidAt(LocalDateTime.now());
            invoice.setBalanceDue(BigDecimal.ZERO);
        } else {
            invoice.setStatus(InvoiceStatus.PARTIALLY_PAID);
        }

        return clientInvoiceRepo.save(invoice);
    }

    public Map<String, Object> getStats() {
        UUID companyId = TenantContext.get();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", clientInvoiceRepo.countByCompanyId(companyId));
        stats.put("sent", clientInvoiceRepo.countByCompanyIdAndStatus(companyId, InvoiceStatus.SENT));
        stats.put("paid", clientInvoiceRepo.countByCompanyIdAndStatus(companyId, InvoiceStatus.PAID));
        stats.put("overdue", clientInvoiceRepo.countByCompanyIdAndStatus(companyId, InvoiceStatus.OVERDUE));

        BigDecimal totalRevenue = clientInvoiceRepo.sumTotalAmountByCompanyIdAndStatusIn(
                companyId, List.of(InvoiceStatus.SENT, InvoiceStatus.VIEWED, InvoiceStatus.PAID,
                        InvoiceStatus.PARTIALLY_PAID, InvoiceStatus.OVERDUE));
        stats.put("totalRevenue", totalRevenue != null ? totalRevenue : BigDecimal.ZERO);

        BigDecimal totalPaid = clientInvoiceRepo.sumAmountPaidByCompanyId(companyId);
        stats.put("totalPaid", totalPaid != null ? totalPaid : BigDecimal.ZERO);

        BigDecimal totalOutstanding = (totalRevenue != null ? totalRevenue : BigDecimal.ZERO)
                .subtract(totalPaid != null ? totalPaid : BigDecimal.ZERO);
        stats.put("totalOutstanding", totalOutstanding);

        return stats;
    }

    public List<ClientInvoice> getOverdue() {
        UUID companyId = TenantContext.get();
        return clientInvoiceRepo.findByCompanyIdAndDueDateBeforeAndStatusNotIn(
                companyId, LocalDate.now(),
                List.of(InvoiceStatus.PAID, InvoiceStatus.CANCELLED));
    }

    public Map<String, Object> getDashboard() {
        UUID companyId = TenantContext.get();
        Map<String, Object> dashboard = new LinkedHashMap<>();

        dashboard.put("stats", getStats());

        List<ClientInvoice> overdue = getOverdue();
        dashboard.put("overdueInvoices", overdue);

        Map<String, Object> aging = new LinkedHashMap<>();
        LocalDate now = LocalDate.now();
        aging.put("current", sumByDueRange(companyId, now, null, false));
        aging.put("days1_30", sumByDueRange(companyId, now.minusDays(30), now, true));
        aging.put("days31_60", sumByDueRange(companyId, now.minusDays(60), now.minusDays(31), true));
        aging.put("days61_90", sumByDueRange(companyId, now.minusDays(90), now.minusDays(61), true));
        aging.put("over90", sumByDueRange(companyId, null, now.minusDays(91), true));
        dashboard.put("agingAnalysis", aging);

        return dashboard;
    }

    private BigDecimal sumByDueRange(UUID companyId, LocalDate from, LocalDate to, boolean checkStatus) {
        List<ClientInvoice> invoices = clientInvoiceRepo.findByCompanyIdOrderByCreatedAtDesc(companyId).stream()
                .filter(i -> i.getStatus() != InvoiceStatus.PAID && i.getStatus() != InvoiceStatus.CANCELLED)
                .filter(i -> i.getDueDate() == null
                        || (from == null || !i.getDueDate().isBefore(from))
                        && (to == null || !i.getDueDate().isAfter(to)))
                .toList();

        return invoices.stream()
                .map(i -> i.getBalanceDue() != null ? i.getBalanceDue() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isValidTransition(InvoiceStatus current, InvoiceStatus next) {
        return switch (current) {
            case DRAFT -> next == InvoiceStatus.SENT || next == InvoiceStatus.CANCELLED;
            case SENT -> next == InvoiceStatus.VIEWED || next == InvoiceStatus.OVERDUE || next == InvoiceStatus.CANCELLED;
            case VIEWED -> next == InvoiceStatus.PAID || next == InvoiceStatus.OVERDUE || next == InvoiceStatus.CANCELLED;
            case PARTIALLY_PAID -> next == InvoiceStatus.PAID || next == InvoiceStatus.OVERDUE || next == InvoiceStatus.CANCELLED;
            case OVERDUE -> next == InvoiceStatus.PAID || next == InvoiceStatus.CANCELLED;
            case PAID, CANCELLED -> false;
        };
    }
}
