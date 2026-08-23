package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Company;
import com.incokalk.model.ShipmentFinancials;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.ShipmentFinancialsRepository;
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

@DisplayName("FinancialReportingService — Tests unitaires")
class FinancialReportingServiceTest {

    @Mock ShipmentFinancialsRepository financialsRepo;
    @Mock CompanyRepository companyRepo;
    @InjectMocks FinancialReportingService service;

    UUID companyId;
    UUID shipId;
    Company company;
    ShipmentFinancials financials;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        shipId = UUID.randomUUID();
        company = Company.builder().id(companyId).build();
        financials = new ShipmentFinancials();
        financials.setId(shipId);
        financials.setRevenue(BigDecimal.valueOf(10000));
        financials.setCostFreight(BigDecimal.valueOf(5000));
        financials.setCostFuel(BigDecimal.valueOf(500));
        financials.setCostHandling(BigDecimal.valueOf(300));
        financials.setCostCustoms(BigDecimal.valueOf(200));
        financials.setCostInsurance(BigDecimal.valueOf(100));
    }

    @Test
    @DisplayName("getAllShipments → liste")
    void getAllShipments() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(financialsRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(financials));
            assertThat(service.getAllShipments()).hasSize(1);
        }
    }

    @Test
    @DisplayName("getShipmentById → trouvé")
    void getShipmentById() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(financialsRepo.findByCompanyIdAndId(companyId, shipId)).thenReturn(Optional.of(financials));
            assertThat(service.getShipmentById(shipId)).isEqualTo(financials);
        }
    }

    @Test
    @DisplayName("getShipmentById → pas trouvé")
    void getShipmentById_notFound() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(financialsRepo.findByCompanyIdAndId(companyId, shipId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getShipmentById(shipId)).isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("create → calcule les financials et sauvegarde")
    void create() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
            when(financialsRepo.save(any())).thenAnswer(i -> i.getArgument(0));

            var result = service.create(financials);
            assertThat(result.getTotalCost()).isEqualTo(BigDecimal.valueOf(6100));
            assertThat(result.getGrossMargin()).isEqualTo(BigDecimal.valueOf(3900));
            assertThat(result.getGrossMarginPercent()).isEqualByComparingTo(new BigDecimal("39.00"));
        }
    }

    @Test
    @DisplayName("getDashboard → calculs corrects")
    void getDashboard() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(financialsRepo.sumRevenueByCompanyId(companyId)).thenReturn(BigDecimal.valueOf(50000));
            when(financialsRepo.sumCostByCompanyId(companyId)).thenReturn(BigDecimal.valueOf(30000));
            when(financialsRepo.sumMarginByCompanyId(companyId)).thenReturn(BigDecimal.valueOf(20000));
            when(financialsRepo.countByCompanyId(companyId)).thenReturn(5L);

            var dashboard = service.getDashboard();
            assertThat((BigDecimal) dashboard.get("totalRevenue")).isEqualByComparingTo(BigDecimal.valueOf(50000));
            assertThat((BigDecimal) dashboard.get("marginPercent")).isEqualByComparingTo(new BigDecimal("40.00"));
            assertThat((BigDecimal) dashboard.get("avgRevenuePerShipment")).isEqualByComparingTo(new BigDecimal("10000.00"));
        }
    }
}
