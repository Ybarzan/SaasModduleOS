package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.ClientInvoice;
import com.incokalk.model.Company;
import com.incokalk.model.InvoiceFinancing;
import com.incokalk.model.PaymentTerm;
import com.incokalk.repository.ClientInvoiceRepository;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.InvoiceFinancingRepository;
import com.incokalk.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("SupplyChainFinanceService — Tests unitaires")
class SupplyChainFinanceServiceTest {

    SupplyChainFinanceService service;
    InvoiceFinancingRepository financingRepo;
    ClientInvoiceRepository clientInvoiceRepo;
    CompanyRepository companyRepo;

    @BeforeEach
    void setUp() {
        financingRepo = mock(InvoiceFinancingRepository.class);
        clientInvoiceRepo = mock(ClientInvoiceRepository.class);
        companyRepo = mock(CompanyRepository.class);
        service = new SupplyChainFinanceService(financingRepo, clientInvoiceRepo, companyRepo);
        TenantContext.set(UUID.randomUUID());
    }

    private Company company() {
        return Company.builder().id(TenantContext.get()).build();
    }

    @Test
    @DisplayName("requestFinancing → succès")
    void requestFinancing_success() {
        UUID companyId = TenantContext.get();
        UUID invoiceId = UUID.randomUUID();

        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company()));
        ClientInvoice invoice = ClientInvoice.builder()
                .id(invoiceId)
                .balanceDue(new BigDecimal("5000"))
                .build();
        when(clientInvoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.of(invoice));
        when(financingRepo.findByInvoiceIdAndCompanyId(invoiceId, companyId)).thenReturn(Optional.empty());
        when(financingRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        InvoiceFinancing result = service.requestFinancing(invoiceId, new BigDecimal("3000"));

        assertThat(result.getRequestedAmount()).isEqualByComparingTo(new BigDecimal("3000"));
        assertThat(result.getStatus()).isEqualTo(InvoiceFinancing.Status.PENDING);
        assertThat(result.getInvoiceId()).isEqualTo(invoiceId);
        assertThat(result.getCompanyId()).isEqualTo(companyId);
    }

    @Test
    @DisplayName("requestFinancing → montant > solde → exception")
    void requestFinancing_amountExceedsBalance() {
        UUID companyId = TenantContext.get();
        UUID invoiceId = UUID.randomUUID();

        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company()));
        ClientInvoice invoice = ClientInvoice.builder()
                .id(invoiceId)
                .balanceDue(new BigDecimal("1000"))
                .build();
        when(clientInvoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.requestFinancing(invoiceId, new BigDecimal("2000")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("requestFinancing → solde nul → exception")
    void requestFinancing_zeroBalance() {
        UUID companyId = TenantContext.get();
        UUID invoiceId = UUID.randomUUID();

        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company()));
        ClientInvoice invoice = ClientInvoice.builder()
                .id(invoiceId)
                .balanceDue(BigDecimal.ZERO)
                .build();
        when(clientInvoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.requestFinancing(invoiceId, new BigDecimal("500")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("solde dû");
    }

    @Test
    @DisplayName("requestFinancing → déjà un financement en cours → exception")
    void requestFinancing_alreadyPending() {
        UUID companyId = TenantContext.get();
        UUID invoiceId = UUID.randomUUID();

        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company()));
        ClientInvoice invoice = ClientInvoice.builder()
                .id(invoiceId)
                .balanceDue(new BigDecimal("5000"))
                .build();
        when(clientInvoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.of(invoice));
        InvoiceFinancing existing = InvoiceFinancing.builder()
                .status(InvoiceFinancing.Status.PENDING)
                .build();
        when(financingRepo.findByInvoiceIdAndCompanyId(invoiceId, companyId)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.requestFinancing(invoiceId, new BigDecimal("1000")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("déjà en cours");
    }

    @Test
    @DisplayName("approveFinancing → succès")
    void approveFinancing_success() {
        UUID companyId = TenantContext.get();
        UUID financingId = UUID.randomUUID();
        InvoiceFinancing financing = InvoiceFinancing.builder()
                .id(financingId)
                .companyId(companyId)
                .status(InvoiceFinancing.Status.PENDING)
                .build();
        when(financingRepo.findByCompanyIdAndId(companyId, financingId)).thenReturn(Optional.of(financing));
        when(financingRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        InvoiceFinancing result = service.approveFinancing(financingId);

        assertThat(result.getStatus()).isEqualTo(InvoiceFinancing.Status.APPROVED);
    }

    @Test
    @DisplayName("approveFinancing → non PENDING → exception")
    void approveFinancing_notPending() {
        UUID companyId = TenantContext.get();
        UUID financingId = UUID.randomUUID();
        InvoiceFinancing financing = InvoiceFinancing.builder()
                .id(financingId)
                .companyId(companyId)
                .status(InvoiceFinancing.Status.APPROVED)
                .build();
        when(financingRepo.findByCompanyIdAndId(companyId, financingId)).thenReturn(Optional.of(financing));

        assertThatThrownBy(() -> service.approveFinancing(financingId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("fundFinancing → succès")
    void fundFinancing_success() {
        UUID companyId = TenantContext.get();
        UUID financingId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        InvoiceFinancing financing = InvoiceFinancing.builder()
                .id(financingId)
                .companyId(companyId)
                .invoiceId(invoiceId)
                .requestedAmount(new BigDecimal("1000"))
                .feePercent(new BigDecimal("2.50"))
                .status(InvoiceFinancing.Status.APPROVED)
                .build();
        ClientInvoice invoice = ClientInvoice.builder()
                .id(invoiceId)
                .balanceDue(new BigDecimal("1000"))
                .build();
        when(financingRepo.findByCompanyIdAndId(companyId, financingId)).thenReturn(Optional.of(financing));
        when(clientInvoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.of(invoice));
        when(financingRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        InvoiceFinancing result = service.fundFinancing(financingId);

        assertThat(result.getStatus()).isEqualTo(InvoiceFinancing.Status.FUNDED);
        assertThat(result.getFeeAmount()).isEqualByComparingTo(new BigDecimal("25.00"));
        assertThat(result.getFinanceAmount()).isEqualByComparingTo(new BigDecimal("975.00"));
    }

    @Test
    @DisplayName("fundFinancing → facture déjà payée entre-temps → exception")
    void fundFinancing_invoiceAlreadyPaid_throws() {
        UUID companyId = TenantContext.get();
        UUID financingId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        InvoiceFinancing financing = InvoiceFinancing.builder()
                .id(financingId)
                .companyId(companyId)
                .invoiceId(invoiceId)
                .requestedAmount(new BigDecimal("1000"))
                .feePercent(new BigDecimal("2.50"))
                .status(InvoiceFinancing.Status.APPROVED)
                .build();
        ClientInvoice invoice = ClientInvoice.builder()
                .id(invoiceId)
                .balanceDue(BigDecimal.ZERO)
                .build();
        when(financingRepo.findByCompanyIdAndId(companyId, financingId)).thenReturn(Optional.of(financing));
        when(clientInvoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.fundFinancing(financingId))
                .isInstanceOf(IllegalStateException.class);

        verify(financingRepo, never()).save(any());
    }

    @Test
    @DisplayName("fundFinancing → non APPROVED → exception")
    void fundFinancing_notApproved() {
        UUID companyId = TenantContext.get();
        UUID financingId = UUID.randomUUID();
        InvoiceFinancing financing = InvoiceFinancing.builder()
                .id(financingId)
                .companyId(companyId)
                .status(InvoiceFinancing.Status.PENDING)
                .build();
        when(financingRepo.findByCompanyIdAndId(companyId, financingId)).thenReturn(Optional.of(financing));

        assertThatThrownBy(() -> service.fundFinancing(financingId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("repayFinancing → succès")
    void repayFinancing_success() {
        UUID companyId = TenantContext.get();
        UUID financingId = UUID.randomUUID();
        InvoiceFinancing financing = InvoiceFinancing.builder()
                .id(financingId)
                .companyId(companyId)
                .status(InvoiceFinancing.Status.FUNDED)
                .build();
        when(financingRepo.findByCompanyIdAndId(companyId, financingId)).thenReturn(Optional.of(financing));
        when(financingRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        InvoiceFinancing result = service.repayFinancing(financingId);

        assertThat(result.getStatus()).isEqualTo(InvoiceFinancing.Status.REPAID);
        assertThat(result.getRepaymentDate()).isEqualTo(LocalDate.now());
    }

    @Test
    @DisplayName("repayFinancing → non FUNDED → exception")
    void repayFinancing_notFunded() {
        UUID companyId = TenantContext.get();
        UUID financingId = UUID.randomUUID();
        InvoiceFinancing financing = InvoiceFinancing.builder()
                .id(financingId)
                .companyId(companyId)
                .status(InvoiceFinancing.Status.PENDING)
                .build();
        when(financingRepo.findByCompanyIdAndId(companyId, financingId)).thenReturn(Optional.of(financing));

        assertThatThrownBy(() -> service.repayFinancing(financingId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("getStats → retourne les indicateurs")
    void getStats() {
        UUID companyId = TenantContext.get();

        when(financingRepo.sumFinanceAmountByCompanyIdAndStatusIn(eq(companyId), anyList()))
                .thenReturn(new BigDecimal("15000"));
        when(financingRepo.countByCompanyIdAndStatus(companyId, InvoiceFinancing.Status.PENDING)).thenReturn(2L);
        when(financingRepo.avgFeePercentByCompanyId(companyId)).thenReturn(new BigDecimal("2.50"));
        when(financingRepo.countByCompanyIdAndStatus(companyId, InvoiceFinancing.Status.FUNDED)).thenReturn(3L);
        when(financingRepo.countByCompanyIdAndStatus(companyId, InvoiceFinancing.Status.REPAID)).thenReturn(5L);

        Map<String, Object> stats = service.getStats();

        assertThat(stats)
                .containsEntry("totalFinanced", new BigDecimal("15000"))
                .containsEntry("pendingCount", 2L)
                .containsEntry("fundedCount", 3L)
                .containsEntry("repaidCount", 5L);
    }

    @Test
    @DisplayName("getEarlyPaymentDiscount → succès")
    void getEarlyPaymentDiscount_success() {
        UUID companyId = TenantContext.get();
        UUID invoiceId = UUID.randomUUID();
        PaymentTerm term = PaymentTerm.builder()
                .earlyPaymentDiscountPercent(new BigDecimal("2.00"))
                .earlyPaymentDiscountDays(10)
                .build();
        ClientInvoice invoice = ClientInvoice.builder()
                .id(invoiceId)
                .invoiceNumber("INV-001")
                .paymentTerm(term)
                .dueDate(LocalDate.now().plusDays(30))
                .build();

        when(clientInvoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.of(invoice));

        Map<String, Object> result = service.getEarlyPaymentDiscount(invoiceId, new BigDecimal("1000"));

        assertThat(result)
                .containsEntry("invoiceId", invoiceId)
                .containsEntry("invoiceNumber", "INV-001")
                .containsEntry("originalAmount", new BigDecimal("1000"))
                .containsEntry("discountPercent", new BigDecimal("2.00"))
                .containsEntry("discountAmount", new BigDecimal("20.00"))
                .containsEntry("discountedAmount", new BigDecimal("980.00"));
    }

    @Test
    @DisplayName("getEarlyPaymentDiscount → pas de discount → exception")
    void getEarlyPaymentDiscount_noDiscount() {
        UUID companyId = TenantContext.get();
        UUID invoiceId = UUID.randomUUID();
        PaymentTerm term = PaymentTerm.builder()
                .earlyPaymentDiscountPercent(BigDecimal.ZERO)
                .build();
        ClientInvoice invoice = ClientInvoice.builder()
                .id(invoiceId)
                .paymentTerm(term)
                .build();

        when(clientInvoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.of(invoice));

        assertThatThrownBy(() -> service.getEarlyPaymentDiscount(invoiceId, new BigDecimal("1000")))
                .isInstanceOf(IllegalStateException.class);
    }
}
