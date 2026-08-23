package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.ClientInvoice;
import com.incokalk.model.Company;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentTermService {

    private final PaymentTermRepository paymentTermRepo;
    private final ClientInvoiceRepository clientInvoiceRepo;
    private final CompanyRepository companyRepo;

    public List<PaymentTerm> getAll() {
        UUID companyId = TenantContext.get();
        return paymentTermRepo.findByCompanyIdAndIsActiveTrue(companyId);
    }

    public PaymentTerm getById(UUID id) {
        UUID companyId = TenantContext.get();
        return paymentTermRepo.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Condition de paiement non trouvée"));
    }

    @Transactional
    public PaymentTerm create(PaymentTerm term) {
        UUID companyId = TenantContext.get();
        term.setCompany(companyRepo.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée")));

        if (term.isDefault()) {
            unsetOtherDefaults(companyId);
        }

        return paymentTermRepo.save(term);
    }

    @Transactional
    public PaymentTerm update(UUID id, PaymentTerm term) {
        UUID companyId = TenantContext.get();
        PaymentTerm existing = paymentTermRepo.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Condition de paiement non trouvée"));

        existing.setName(term.getName());
        existing.setCode(term.getCode());
        existing.setDescription(term.getDescription());
        existing.setDaysUntilDue(term.getDaysUntilDue());
        existing.setEarlyPaymentDiscountPercent(term.getEarlyPaymentDiscountPercent());
        existing.setEarlyPaymentDiscountDays(term.getEarlyPaymentDiscountDays());
        existing.setLateFeePercent(term.getLateFeePercent());
        existing.setActive(term.isActive());
        existing.setDefault(term.isDefault());

        if (term.isDefault()) {
            unsetOtherDefaults(companyId);
        }

        return paymentTermRepo.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        UUID companyId = TenantContext.get();
        PaymentTerm term = paymentTermRepo.findByCompanyIdAndId(companyId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Condition de paiement non trouvée"));

        List<ClientInvoice> invoices = clientInvoiceRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
        for (ClientInvoice invoice : invoices) {
            if (invoice.getPaymentTerm() != null && invoice.getPaymentTerm().getId().equals(id)) {
                throw new IllegalArgumentException("Cette condition de paiement est utilisée par des factures clients");
            }
        }

        paymentTermRepo.delete(term);
    }

    public PaymentTerm getDefault() {
        UUID companyId = TenantContext.get();
        return paymentTermRepo.findByCompanyIdAndIsDefaultTrue(companyId).orElse(null);
    }

    @Transactional
    public void seedDefaults() {
        UUID companyId = TenantContext.get();
        List<PaymentTerm> existing = paymentTermRepo.findByCompanyIdAndIsActiveTrue(companyId);
        if (!existing.isEmpty()) {
            return;
        }

        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée"));

        List<PaymentTerm> defaults = List.of(
                PaymentTerm.builder().company(company).name("Net 15").code("NET15")
                        .description("Paiement sous 15 jours").daysUntilDue(15).isActive(true).build(),
                PaymentTerm.builder().company(company).name("Net 30").code("NET30")
                        .description("Paiement sous 30 jours").daysUntilDue(30).isActive(true).isDefault(true).build(),
                PaymentTerm.builder().company(company).name("Net 60").code("NET60")
                        .description("Paiement sous 60 jours").daysUntilDue(60).isActive(true).build(),
                PaymentTerm.builder().company(company).name("Net 90").code("NET90")
                        .description("Paiement sous 90 jours").daysUntilDue(90).isActive(true).build(),
                PaymentTerm.builder().company(company).name("2/10 Net 30").code("2_10_NET30")
                        .description("2% de réduction si payé sous 10 jours, sinon sous 30 jours")
                        .daysUntilDue(30).earlyPaymentDiscountPercent(BigDecimal.valueOf(2))
                        .earlyPaymentDiscountDays(10).isActive(true).build(),
                PaymentTerm.builder().company(company).name("Cash on Delivery").code("COD")
                        .description("Paiement à la livraison").daysUntilDue(0).isActive(true).build()
        );

        paymentTermRepo.saveAll(defaults);
        log.info("Conditions de paiement par défaut créées pour la société {}", companyId);
    }

    private void unsetOtherDefaults(UUID companyId) {
        paymentTermRepo.findByCompanyIdAndIsDefaultTrue(companyId).ifPresent(current -> {
            current.setDefault(false);
            paymentTermRepo.save(current);
        });
    }
}
