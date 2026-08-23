package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.CarbonOffset;
import com.incokalk.model.Company;
import com.incokalk.repository.CarbonOffsetRepository;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.tenant.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("CarbonOffsetService — Tests unitaires")
class CarbonOffsetServiceTest {

    @Mock CarbonOffsetRepository carbonOffsetRepo;
    @Mock CompanyRepository companyRepo;
    @InjectMocks CarbonOffsetService service;

    UUID companyId;
    UUID offsetId;
    Company company;
    CarbonOffset offset;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        offsetId = UUID.randomUUID();
        company = Company.builder().id(companyId).build();
        offset = CarbonOffset.builder().id(offsetId).co2EmissionsKg(BigDecimal.valueOf(1000)).build();
    }

    @Test
    @DisplayName("getAll → liste")
    void getAll() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(carbonOffsetRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(offset));
            assertThat(service.getAll()).hasSize(1);
        }
    }

    @Test
    @DisplayName("getById → trouvé")
    void getById_found() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(carbonOffsetRepo.findByCompanyIdAndId(companyId, offsetId)).thenReturn(Optional.of(offset));
            assertThat(service.getById(offsetId)).isEqualTo(offset);
        }
    }

    @Test
    @DisplayName("getById → pas trouvé")
    void getById_notFound() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(carbonOffsetRepo.findByCompanyIdAndId(companyId, offsetId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getById(offsetId)).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("create → succès")
    void create() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
            when(carbonOffsetRepo.save(any())).thenReturn(offset);
            assertThat(service.create(offset)).isEqualTo(offset);
        }
    }

    @Test
    @DisplayName("delete → succès")
    void delete() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(carbonOffsetRepo.findByCompanyIdAndId(companyId, offsetId)).thenReturn(Optional.of(offset));
            service.delete(offsetId);
            verify(carbonOffsetRepo).delete(offset);
        }
    }

    @Test
    @DisplayName("getStats → émissions nettes")
    void getStats() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(carbonOffsetRepo.sumCo2EmissionsKgByCompanyId(companyId)).thenReturn(BigDecimal.valueOf(10000));
            when(carbonOffsetRepo.sumOffsetCreditsRetiredByCompanyId(companyId)).thenReturn(BigDecimal.valueOf(4000));
            when(carbonOffsetRepo.sumOffsetTotalCostByCompanyId(companyId)).thenReturn(BigDecimal.valueOf(200));
            when(carbonOffsetRepo.countByCompanyIdAndStatus(eq(companyId), any())).thenReturn(0L);

            var stats = service.getStats();
            assertThat(stats.get("totalEmissions")).isEqualTo(BigDecimal.valueOf(10000));
            assertThat(stats.get("netEmissions")).isEqualTo(BigDecimal.valueOf(6000));
        }
    }
}
