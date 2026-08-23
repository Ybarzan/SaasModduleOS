package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.CarrierInvoice;
import com.incokalk.model.CarrierInvoice.InvoiceStatus;
import com.incokalk.model.Company;
import com.incokalk.repository.CarrierInvoiceLineRepository;
import com.incokalk.repository.CarrierInvoiceRepository;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("CarrierInvoiceService — Tests unitaires")
class CarrierInvoiceServiceTest {

    @Mock CarrierInvoiceRepository invoiceRepo;
    @Mock CarrierInvoiceLineRepository lineRepo;
    @Mock CompanyRepository companyRepo;
    @InjectMocks CarrierInvoiceService service;

    UUID companyId;
    UUID invoiceId;
    Company company;
    CarrierInvoice invoice;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        invoiceId = UUID.randomUUID();
        company = Company.builder().id(companyId).build();
        invoice = CarrierInvoice.builder()
                .id(invoiceId)
                .company(company)
                .invoiceNumber("CI-001")
                .invoiceDate(LocalDate.now())
                .status(InvoiceStatus.RECEIVED)
                .totalAmount(BigDecimal.valueOf(5000))
                .currency("EUR")
                .exchangeRate(BigDecimal.ONE)
                .totalAmountEur(BigDecimal.valueOf(5000))
                .lines(new ArrayList<>())
                .build();
    }

    @Test
    @DisplayName("getAll → list without paging")
    void getAll() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(invoiceRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(invoice));
            assertThat(service.getAll()).hasSize(1);
        }
    }

    @Test
    @DisplayName("getAll → list with paging")
    void getAll_paged() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            Page<CarrierInvoice> page = Page.empty();
            when(invoiceRepo.findByCompanyIdOrderByCreatedAtDesc(eq(companyId), any(Pageable.class))).thenReturn(page);
            assertThat(service.getAll(Pageable.unpaged())).isEmpty();
        }
    }

    @Test
    @DisplayName("getById → found")
    void getById_found() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(invoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.of(invoice));
            assertThat(service.getById(invoiceId)).isEqualTo(invoice);
        }
    }

    @Test
    @DisplayName("getById → not found throws")
    void getById_notFound() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(invoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getById(invoiceId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("create → success with exchange rate")
    void create_withExchangeRate() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            invoice.setExchangeRate(BigDecimal.valueOf(0.9));
            invoice.setTotalAmountEur(null);
            when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
            when(invoiceRepo.save(any(CarrierInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = service.create(invoice);
            assertThat(result.getTotalAmountEur()).isEqualByComparingTo(BigDecimal.valueOf(4500.00));
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
    @DisplayName("updateStatus → valid transition to APPROVED")
    void updateStatus_approved() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(invoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.of(invoice));
            when(invoiceRepo.save(any(CarrierInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = service.updateStatus(invoiceId, InvoiceStatus.UNDER_REVIEW, null);
            assertThat(result.getStatus()).isEqualTo(InvoiceStatus.UNDER_REVIEW);
        }
    }

    @Test
    @DisplayName("updateStatus → invalid transition throws")
    void updateStatus_invalid() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(invoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.of(invoice));
            assertThatThrownBy(() -> service.updateStatus(invoiceId, InvoiceStatus.PAID, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("delete → success for DRAFT invoice")
    void delete_draft() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            invoice.setStatus(InvoiceStatus.DRAFT);
            when(invoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.of(invoice));
            doNothing().when(lineRepo).deleteByInvoiceId(invoiceId);
            doNothing().when(invoiceRepo).delete(invoice);
            service.delete(invoiceId);
            verify(lineRepo).deleteByInvoiceId(invoiceId);
            verify(invoiceRepo).delete(invoice);
        }
    }

    @Test
    @DisplayName("delete → non-draft/received throws")
    void delete_nonDraft() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            invoice.setStatus(InvoiceStatus.APPROVED);
            when(invoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.of(invoice));
            assertThatThrownBy(() -> service.delete(invoiceId))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("getStats → returns stats map")
    void getStats() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(invoiceRepo.countByCompanyId(companyId)).thenReturn(10L);
            when(invoiceRepo.countByCompanyIdAndStatus(companyId, InvoiceStatus.RECEIVED)).thenReturn(5L);
            when(invoiceRepo.countByCompanyIdAndStatus(companyId, InvoiceStatus.UNDER_REVIEW)).thenReturn(2L);
            when(invoiceRepo.countByCompanyIdAndStatus(companyId, InvoiceStatus.APPROVED)).thenReturn(2L);
            when(invoiceRepo.countByCompanyIdAndStatus(companyId, InvoiceStatus.PAID)).thenReturn(1L);
            when(invoiceRepo.countByCompanyIdAndStatus(companyId, InvoiceStatus.DISPUTED)).thenReturn(0L);
            when(invoiceRepo.countByCompanyIdAndStatus(companyId, InvoiceStatus.REJECTED)).thenReturn(0L);
            when(invoiceRepo.sumTotalAmountEurByCompanyIdAndStatusIn(eq(companyId), anyList()))
                    .thenReturn(BigDecimal.valueOf(9000));

            Map<String, Object> stats = service.getStats();
            assertThat(stats.get("total")).isEqualTo(10L);
            assertThat(stats.get("received")).isEqualTo(5L);
            assertThat(stats.get("approved")).isEqualTo(2L);
            assertThat(stats.get("paid")).isEqualTo(1L);
            assertThat(stats.get("totalAmountEur")).isEqualTo(BigDecimal.valueOf(9000));
        }
    }

    @Test
    @DisplayName("reconcile → success with variance")
    void reconcile() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            invoice.setTotalAmount(BigDecimal.valueOf(5000));
            when(invoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.of(invoice));
            when(invoiceRepo.save(any(CarrierInvoice.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = service.reconcile(invoiceId, BigDecimal.valueOf(4800), "Negotiated rate");
            assertThat(result.getNegotiatedRate()).isEqualByComparingTo(BigDecimal.valueOf(4800));
            assertThat(result.getVariance()).isEqualByComparingTo(BigDecimal.valueOf(200));
            assertThat(result.getVariancePercent()).isEqualByComparingTo(BigDecimal.valueOf(4.17));
            assertThat(result.getReconciliationNotes()).isEqualTo("Negotiated rate");
        }
    }

    @Test
    @DisplayName("reconcile → invoice not found throws")
    void reconcile_notFound() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(invoiceRepo.findByCompanyIdAndId(companyId, invoiceId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.reconcile(invoiceId, BigDecimal.valueOf(4800), "notes"))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
