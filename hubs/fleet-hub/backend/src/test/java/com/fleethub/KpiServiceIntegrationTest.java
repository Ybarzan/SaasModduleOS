package com.fleethub;

import com.fleethub.dto.CoupleKpiDto;
import com.fleethub.dto.TruckDetailDto;
import com.fleethub.model.Company;
import com.fleethub.model.Truck;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.repository.TruckRepository;
import com.fleethub.security.TenantContext;
import com.fleethub.service.KpiService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class KpiServiceIntegrationTest {

    @Autowired
    private KpiService kpiService;

    @Autowired
    private TruckRepository truckRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @BeforeEach
    void setTenant() {
        Company demo = companyRepository.findByName("Fleet Hub Démo")
                .orElseThrow(() -> new IllegalStateException("Société de démo absente"));
        TenantContext.set(demo);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void computeAllCouples_returnsValidKpis() {
        List<CoupleKpiDto> couples = kpiService.computeAllCouples("MONTH");
        assertFalse(couples.isEmpty(), "Des couples doivent être calculés");
        assertEquals(6, couples.size());

        for (CoupleKpiDto k : couples) {
            assertTrue(k.performanceScore() >= 0 && k.performanceScore() <= 100, "Score hors bornes");
            assertTrue(k.utilizationRate() >= 0 && k.utilizationRate() <= 100, "Utilisation hors bornes");
            assertTrue(k.maintenanceComplianceRate() >= 0 && k.maintenanceComplianceRate() <= 100);
            assertTrue(k.unplannedDowntimeRate() >= 0 && k.unplannedDowntimeRate() <= 100);
            assertTrue(k.totalKm() >= 0, "KM négatif");
            assertTrue(k.driverName() != null && !k.driverName().isBlank());
            assertTrue(k.registration() != null && !k.registration().isBlank());
        }
    }

    @Test
    void computeAllCouples_supportsAllPeriods() {
        for (String period : List.of("DAY", "WEEK", "MONTH")) {
            List<CoupleKpiDto> couples = kpiService.computeAllCouples(period);
            assertFalse(couples.isEmpty(), "Période " + period + " doit produire des résultats");
        }
    }

    @Test
    void costPerKm_isStrictlyPositive_forAtLeastOneCouple() {
        List<CoupleKpiDto> couples = kpiService.computeAllCouples("MONTH");
        boolean anyPositive = couples.stream().anyMatch(k -> k.costPerKm() > 0);
        assertTrue(anyPositive, "Au moins un couple doit avoir un coût au km calculé");
    }

    @Test
    void computeTruckDetail_returnsKpisAndActivity() {
        Truck t = truckRepository.findByCompanyId(TenantContext.companyId()).get(0);
        TruckDetailDto detail = kpiService.computeTruckDetail(t.getId(), "MONTH");

        assertNotNull(detail.kpis());
        assertEquals(t.getId(), detail.kpis().truckId());
        assertTrue(detail.kpis().truckUptimeRate() >= 0 && detail.kpis().truckUptimeRate() <= 100);
        assertTrue(detail.kpis().consumptionPer100Km() > 0, "La consommation doit être calculée");
        assertNotNull(detail.dailyTrend());
        assertFalse(detail.dailyTrend().isEmpty(), "La tendance journalière doit être renseignée");
        assertNotNull(detail.trips());
        assertNotNull(detail.fuels());
        assertNotNull(detail.maintenance());
        assertNotNull(detail.costBreakdown());
    }
}
