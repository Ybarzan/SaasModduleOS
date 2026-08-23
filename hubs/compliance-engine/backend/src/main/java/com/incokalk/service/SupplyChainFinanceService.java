package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.ClientInvoice;
import com.incokalk.model.InvoiceFinancing;
import com.incokalk.repository.ClientInvoiceRepository;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.InvoiceFinancingRepository;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SupplyChainFinanceService {

    private static final BigDecimal DEFAULT_FEE_PERCENT = new BigDecimal("2.50");

    private final InvoiceFinancingRepository financingRepo;
    private final ClientInvoiceRepository clientInvoiceRepo;
    private final CompanyRepository companyRepo;

    @Transactional
    public InvoiceFinancing requestFinancing(UUID invoiceId, BigDecimal amount) {
        UUID companyId = TenantContext.get();

        companyRepo.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée"));

        ClientInvoice invoice = clientInvoiceRepo.findByCompanyIdAndId(companyId, invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Facture non trouvée"));

        if (invoice.getBalanceDue() == null || invoice.getBalanceDue().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Cette facture n'a pas de solde dû");
        }

        if (amount.compareTo(invoice.getBalanceDue()) > 0) {
            throw new IllegalArgumentException("Le montant demandé ne peut pas dépasser le solde dû de " + invoice.getBalanceDue());
        }

        financingRepo.findByInvoiceIdAndCompanyId(invoiceId, companyId).ifPresent(f -> {
            if (f.getStatus() == InvoiceFinancing.Status.PENDING || f.getStatus() == InvoiceFinancing.Status.APPROVED) {
                throw new IllegalStateException("Un financement est déjà en cours pour cette facture");
            }
        });

        InvoiceFinancing financing = InvoiceFinancing.builder()
                .invoiceId(invoiceId)
                .companyId(companyId)
                .requestedAmount(amount)
                .feePercent(DEFAULT_FEE_PERCENT)
                .status(InvoiceFinancing.Status.PENDING)
                .requestedAt(LocalDateTime.now())
                .build();

        InvoiceFinancing saved = financingRepo.save(financing);
        log.info("Demande de financement créée {} pour la facture {} montant {}", saved.getId(), invoiceId, amount);
        return saved;
    }

    @Transactional
    public InvoiceFinancing approveFinancing(UUID financingId) {
        UUID companyId = TenantContext.get();
        InvoiceFinancing financing = financingRepo.findByCompanyIdAndId(companyId, financingId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de financement non trouvée"));

        if (financing.getStatus() != InvoiceFinancing.Status.PENDING) {
            throw new IllegalStateException("Seules les demandes en attente peuvent être approuvées");
        }

        financing.setStatus(InvoiceFinancing.Status.APPROVED);
        InvoiceFinancing saved = financingRepo.save(financing);
        log.info("Demande de financement {} approuvée", financingId);
        return saved;
    }

    @Transactional
    public InvoiceFinancing fundFinancing(UUID financingId) {
        UUID companyId = TenantContext.get();
        InvoiceFinancing financing = financingRepo.findByCompanyIdAndId(companyId, financingId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de financement non trouvée"));

        if (financing.getStatus() != InvoiceFinancing.Status.APPROVED) {
            throw new IllegalStateException("Seules les demandes approuvées peuvent être financées");
        }

        ClientInvoice invoice = clientInvoiceRepo.findByCompanyIdAndId(companyId, financing.getInvoiceId())
                .orElseThrow(() -> new ResourceNotFoundException("Facture non trouvée"));
        BigDecimal balanceDue = invoice.getBalanceDue() != null ? invoice.getBalanceDue() : BigDecimal.ZERO;
        if (balanceDue.compareTo(financing.getRequestedAmount()) < 0) {
            throw new IllegalStateException(
                    "Le solde de la facture (" + balanceDue + ") est désormais inférieur au montant demandé ("
                            + financing.getRequestedAmount() + ") — la facture a probablement été payée depuis. "
                            + "Annulez cette demande de financement.");
        }

        BigDecimal feeAmount = financing.getRequestedAmount()
                .multiply(financing.getFeePercent())
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal financeAmount = financing.getRequestedAmount().subtract(feeAmount);

        financing.setFeeAmount(feeAmount);
        financing.setFinanceAmount(financeAmount);
        financing.setFundedAt(LocalDateTime.now());
        financing.setStatus(InvoiceFinancing.Status.FUNDED);

        InvoiceFinancing saved = financingRepo.save(financing);
        log.info("Financement {} effectué: montant net {} frais {}", financingId, financeAmount, feeAmount);
        return saved;
    }

    @Transactional
    public InvoiceFinancing repayFinancing(UUID financingId) {
        UUID companyId = TenantContext.get();
        InvoiceFinancing financing = financingRepo.findByCompanyIdAndId(companyId, financingId)
                .orElseThrow(() -> new ResourceNotFoundException("Demande de financement non trouvée"));

        if (financing.getStatus() != InvoiceFinancing.Status.FUNDED) {
            throw new IllegalStateException("Seuls les financements actifs peuvent être remboursés");
        }

        financing.setStatus(InvoiceFinancing.Status.REPAID);
        financing.setRepaymentDate(LocalDate.now());

        InvoiceFinancing saved = financingRepo.save(financing);
        log.info("Financement {} remboursé", financingId);
        return saved;
    }

    public List<InvoiceFinancing> getFinancingHistory() {
        UUID companyId = TenantContext.get();
        return financingRepo.findByCompanyIdOrderByRequestedAtDesc(companyId);
    }

    public Map<String, Object> getStats() {
        UUID companyId = TenantContext.get();

        BigDecimal totalFinanced = financingRepo.sumFinanceAmountByCompanyIdAndStatusIn(companyId,
                List.of(InvoiceFinancing.Status.FUNDED, InvoiceFinancing.Status.REPAID));
        long pendingCount = financingRepo.countByCompanyIdAndStatus(companyId, InvoiceFinancing.Status.PENDING);
        BigDecimal avgFee = financingRepo.avgFeePercentByCompanyId(companyId);
        long fundedCount = financingRepo.countByCompanyIdAndStatus(companyId, InvoiceFinancing.Status.FUNDED);
        long repaidCount = financingRepo.countByCompanyIdAndStatus(companyId, InvoiceFinancing.Status.REPAID);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalFinanced", totalFinanced);
        stats.put("pendingCount", pendingCount);
        stats.put("fundedCount", fundedCount);
        stats.put("repaidCount", repaidCount);
        stats.put("averageFeePercent", avgFee.setScale(2, RoundingMode.HALF_UP));
        return stats;
    }

    public Map<String, Object> getEarlyPaymentDiscount(UUID invoiceId, BigDecimal amount) {
        UUID companyId = TenantContext.get();

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Le montant doit être positif");
        }

        ClientInvoice invoice = clientInvoiceRepo.findByCompanyIdAndId(companyId, invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Facture non trouvée"));

        if (invoice.getPaymentTerm() == null) {
            throw new IllegalStateException("Cette facture n'a pas de condition de paiement associée");
        }

        BigDecimal discountPercent = invoice.getPaymentTerm().getEarlyPaymentDiscountPercent();
        Integer discountDays = invoice.getPaymentTerm().getEarlyPaymentDiscountDays();

        if (discountPercent == null || discountPercent.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException("Aucun escompte pour paiement anticipé disponible pour cette facture");
        }

        BigDecimal discountAmount = amount
                .multiply(discountPercent)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        BigDecimal discountedAmount = amount.subtract(discountAmount);

        LocalDate deadline = invoice.getDueDate() != null
                ? invoice.getDueDate().minusDays(discountDays != null ? discountDays : 0)
                : null;

        Map<String, Object> result = new HashMap<>();
        result.put("invoiceId", invoiceId);
        result.put("invoiceNumber", invoice.getInvoiceNumber());
        result.put("originalAmount", amount);
        result.put("discountPercent", discountPercent);
        result.put("discountAmount", discountAmount);
        result.put("discountedAmount", discountedAmount);
        result.put("discountDeadline", deadline);
        result.put("daysUntilDeadline", deadline != null
                ? LocalDate.now().until(deadline).getDays()
                : null);
        return result;
    }
}
