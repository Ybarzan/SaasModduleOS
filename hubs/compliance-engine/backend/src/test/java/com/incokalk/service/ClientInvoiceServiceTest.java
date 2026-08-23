package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.ClientInvoice;
import com.incokalk.model.ClientInvoice.InvoiceStatus;
import com.incokalk.model.Company;
import com.incokalk.model.PaymentTerm;
import com.incokalk.repository.ClientInvoiceRepository;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.PaymentTermRepository;
import com.incokalk.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ClientInvoiceService — Tests unitaires")
class ClientInvoiceServiceTest {

    @Mock ClientInvoiceRepository clientInvoiceRepo;
    @Mock PaymentTermRepository paymentTermRepo;
    @Mock CompanyRepository companyRepo;
    @InjectMocks ClientInvoiceService service;

    UUID companyId;
    UUID invoiceId;
    Company company;
    ClientInvoice invoice;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        invoiceId = UUID.randomUUID();
        company = Company.builder().id(companyId).build();
        invoice = ClientInvoice.builder()
                .id(invoiceId)
                .company(company)
                .invoiceNumber("INV-001")
                .invoiceDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .clientName("Test Client")
                .subtotal(BigDecimal.valueOf(1000))
                .vatAmount(BigDecimal.valueOf(200))
                .totalAmount(BigDecimal.valueOf(1200))
                .amountPaid(BigDecimal.ZERO)
                .balanceDue(BigDecimal.valueOf(1200))
                .currency("EUR")
                .status(InvoiceStatus.DRAFT)
                .build();
    }

    @Test
    @DisplayName("getAll → returns invoices for company")
    void getAll() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(clientInvoiceRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(invoice));
            assertThat(service.getAll()).hasSize(1);
        }
    }

    @Test
    @DisplayName("getById → found")
    void getById_found() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(clientInvoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.of(invoice));
            assertThat(service.getById(invoiceId)).isEqualTo(invoice);
        }
    }

    @Test
    @DisplayName("getById → not found throws")
    void getById_notFound() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(clientInvoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getById(invoiceId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("create → success with payment term")
    void create_withPaymentTerm() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            var term = PaymentTerm.builder().daysUntilDue(15).build();
            invoice.setPaymentTerm(term);
            invoice.setDueDate(null);
            when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
            when(clientInvoiceRepo.save(any(ClientInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = service.create(invoice);
            assertThat(result.getCompany()).isEqualTo(company);
            assertThat(result.getDueDate()).isEqualTo(invoice.getInvoiceDate().plusDays(15));
            assertThat(result.getBalanceDue()).isEqualByComparingTo(BigDecimal.valueOf(1200));
        }
    }

    @Test
    @DisplayName("create → company not found throws")
    void create_companyNotFound() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(companyRepo.findById(companyId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.create(invoice))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("update → success")
    void update_success() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(clientInvoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.of(invoice));
            when(clientInvoiceRepo.save(any(ClientInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

            var updated = ClientInvoice.builder()
                    .invoiceNumber("INV-002")
                    .totalAmount(BigDecimal.valueOf(2000))
                    .amountPaid(BigDecimal.ZERO)
                    .build();
            var result = service.update(invoiceId, updated);
            assertThat(result.getInvoiceNumber()).isEqualTo("INV-002");
            assertThat(result.getBalanceDue()).isEqualByComparingTo(BigDecimal.valueOf(2000));
        }
    }

    @Test
    @DisplayName("update → not found throws")
    void update_notFound() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(clientInvoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.update(invoiceId, invoice))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("delete → success for draft invoice")
    void delete_draft() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(clientInvoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.of(invoice));
            doNothing().when(clientInvoiceRepo).delete(invoice);
            service.delete(invoiceId);
            verify(clientInvoiceRepo).delete(invoice);
        }
    }

    @Test
    @DisplayName("delete → non-draft throws")
    void delete_nonDraft() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            invoice.setStatus(InvoiceStatus.SENT);
            when(clientInvoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.of(invoice));
            assertThatThrownBy(() -> service.delete(invoiceId))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("updateStatus → valid transition")
    void updateStatus_valid() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(clientInvoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.of(invoice));
            when(clientInvoiceRepo.save(any(ClientInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = service.updateStatus(invoiceId, InvoiceStatus.SENT);
            assertThat(result.getStatus()).isEqualTo(InvoiceStatus.SENT);
        }
    }

    @Test
    @DisplayName("updateStatus → paid sets paidAt and amountPaid")
    void updateStatus_paid() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            invoice.setStatus(InvoiceStatus.VIEWED);
            when(clientInvoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.of(invoice));
            when(clientInvoiceRepo.save(any(ClientInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = service.updateStatus(invoiceId, InvoiceStatus.PAID);
            assertThat(result.getStatus()).isEqualTo(InvoiceStatus.PAID);
            assertThat(result.getPaidAt()).isNotNull();
            assertThat(result.getAmountPaid()).isEqualByComparingTo(BigDecimal.valueOf(1200));
            assertThat(result.getBalanceDue()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Test
    @DisplayName("updateStatus → invalid transition throws")
    void updateStatus_invalid() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            invoice.setStatus(InvoiceStatus.PAID);
            when(clientInvoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.of(invoice));
            assertThatThrownBy(() -> service.updateStatus(invoiceId, InvoiceStatus.SENT))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("recordPayment → success partial")
    void recordPayment_partial() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            invoice.setStatus(InvoiceStatus.SENT);
            when(clientInvoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.of(invoice));
            when(clientInvoiceRepo.save(any(ClientInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = service.recordPayment(invoiceId, BigDecimal.valueOf(500), "REF-PAY-001");
            assertThat(result.getAmountPaid()).isEqualByComparingTo(BigDecimal.valueOf(500));
            assertThat(result.getBalanceDue()).isEqualByComparingTo(BigDecimal.valueOf(700));
            assertThat(result.getStatus()).isEqualTo(InvoiceStatus.PARTIALLY_PAID);
            assertThat(result.getPaymentReference()).isEqualTo("REF-PAY-001");
        }
    }

    @Test
    @DisplayName("recordPayment → success full")
    void recordPayment_full() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            invoice.setStatus(InvoiceStatus.SENT);
            when(clientInvoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.of(invoice));
            when(clientInvoiceRepo.save(any(ClientInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = service.recordPayment(invoiceId, BigDecimal.valueOf(1200), "REF-PAY-002");
            assertThat(result.getStatus()).isEqualTo(InvoiceStatus.PAID);
            assertThat(result.getBalanceDue()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getPaidAt()).isNotNull();
        }
    }

    @Test
    @DisplayName("recordPayment → zero amount throws")
    void recordPayment_zeroAmount() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(clientInvoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.of(invoice));
            assertThatThrownBy(() -> service.recordPayment(invoiceId, BigDecimal.ZERO, "ref"))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("getStats → returns stats map")
    void getStats() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(clientInvoiceRepo.countByCompanyId(companyId)).thenReturn(5L);
            when(clientInvoiceRepo.countByCompanyIdAndStatus(companyId, InvoiceStatus.SENT)).thenReturn(2L);
            when(clientInvoiceRepo.countByCompanyIdAndStatus(companyId, InvoiceStatus.PAID)).thenReturn(2L);
            when(clientInvoiceRepo.countByCompanyIdAndStatus(companyId, InvoiceStatus.OVERDUE)).thenReturn(0L);
            when(clientInvoiceRepo.sumTotalAmountByCompanyIdAndStatusIn(eq(companyId), anyList()))
                    .thenReturn(BigDecimal.valueOf(10000));
            when(clientInvoiceRepo.sumAmountPaidByCompanyId(companyId)).thenReturn(BigDecimal.valueOf(4000));

            Map<String, Object> stats = service.getStats();
            assertThat(stats.get("total")).isEqualTo(5L);
            assertThat(stats.get("sent")).isEqualTo(2L);
            assertThat(stats.get("paid")).isEqualTo(2L);
            assertThat(stats.get("overdue")).isEqualTo(0L);
            assertThat(stats.get("totalRevenue")).isEqualTo(BigDecimal.valueOf(10000));
            assertThat(stats.get("totalPaid")).isEqualTo(BigDecimal.valueOf(4000));
            assertThat(stats.get("totalOutstanding")).isEqualTo(BigDecimal.valueOf(6000));
        }
    }

    @Test
    @DisplayName("getOverdue → returns overdue invoices")
    void getOverdue() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(clientInvoiceRepo.findByCompanyIdAndDueDateBeforeAndStatusNotIn(eq(companyId), any(LocalDate.class), anyList()))
                    .thenReturn(List.of(invoice));
            assertThat(service.getOverdue()).hasSize(1);
        }
    }

    @Test
    @DisplayName("getDashboard → returns dashboard with stats and overdue")
    void getDashboard() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(clientInvoiceRepo.countByCompanyId(companyId)).thenReturn(1L);
            when(clientInvoiceRepo.countByCompanyIdAndStatus(companyId, InvoiceStatus.SENT)).thenReturn(1L);
            when(clientInvoiceRepo.countByCompanyIdAndStatus(companyId, InvoiceStatus.PAID)).thenReturn(0L);
            when(clientInvoiceRepo.countByCompanyIdAndStatus(companyId, InvoiceStatus.OVERDUE)).thenReturn(0L);
            when(clientInvoiceRepo.sumTotalAmountByCompanyIdAndStatusIn(eq(companyId), anyList()))
                    .thenReturn(BigDecimal.valueOf(1200));
            when(clientInvoiceRepo.sumAmountPaidByCompanyId(companyId)).thenReturn(BigDecimal.ZERO);
            when(clientInvoiceRepo.findByCompanyIdAndDueDateBeforeAndStatusNotIn(eq(companyId), any(LocalDate.class), anyList()))
                    .thenReturn(List.of());

            var dashboard = service.getDashboard();
            assertThat(dashboard.get("stats")).isNotNull();
            assertThat(dashboard.get("overdueInvoices")).isNotNull();
            assertThat(dashboard.get("agingAnalysis")).isNotNull();
        }
    }
}
