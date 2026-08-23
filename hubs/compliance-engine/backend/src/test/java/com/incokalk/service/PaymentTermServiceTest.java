package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.ClientInvoice;
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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("PaymentTermService — Tests unitaires")
class PaymentTermServiceTest {

    @Mock PaymentTermRepository paymentTermRepo;
    @Mock ClientInvoiceRepository clientInvoiceRepo;
    @Mock CompanyRepository companyRepo;

    @InjectMocks PaymentTermService service;

    UUID companyId;
    UUID termId;
    Company company;
    PaymentTerm term;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        termId = UUID.randomUUID();
        company = Company.builder().id(companyId).build();
        term = PaymentTerm.builder().id(termId).name("Net 30").code("NET30")
                .daysUntilDue(30).isActive(true).build();
    }

    @Test
    @DisplayName("getAll → liste des terms")
    void getAll() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(paymentTermRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of(term));
            assertThat(service.getAll()).hasSize(1);
        }
    }

    @Test
    @DisplayName("getById → trouve")
    void getById_found() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(paymentTermRepo.findByCompanyIdAndId(companyId, termId)).thenReturn(Optional.of(term));
            assertThat(service.getById(termId)).isEqualTo(term);
        }
    }

    @Test
    @DisplayName("getById → pas trouvé → exception")
    void getById_notFound() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(paymentTermRepo.findByCompanyIdAndId(companyId, termId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getById(termId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("create → succès")
    void create() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
            when(paymentTermRepo.save(any())).thenReturn(term);
            PaymentTerm created = service.create(term);
            assertThat(created).isEqualTo(term);
        }
    }

    @Test
    @DisplayName("delete → lié à une facture → exception")
    void delete_linkedToInvoice() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(paymentTermRepo.findByCompanyIdAndId(companyId, termId)).thenReturn(Optional.of(term));
            ClientInvoice inv = new ClientInvoice();
            inv.setPaymentTerm(term);
            when(clientInvoiceRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(inv));
            assertThatThrownBy(() -> service.delete(termId))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("delete → non lié → succès")
    void delete_unlinked() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(paymentTermRepo.findByCompanyIdAndId(companyId, termId)).thenReturn(Optional.of(term));
            when(clientInvoiceRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of());
            service.delete(termId);
            verify(paymentTermRepo).delete(term);
        }
    }
}
