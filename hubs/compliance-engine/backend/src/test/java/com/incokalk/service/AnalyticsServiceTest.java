package com.incokalk.service;

import com.incokalk.dto.analytics.DashboardStatsDTO;
import com.incokalk.repository.SimulationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Toutes les méthodes sauf getDashboardStats ont été retirées avec le domaine
 * Transport (voir AnalyticsService) ; getDashboardStats elle-même est réduite à
 * ses deux champs indépendants du domaine Transport (totalSimulations/
 * simulationsThisMonth, via SimulationRepository).
 */
@DisplayName("AnalyticsService — Tests unitaires")
class AnalyticsServiceTest {

    @Mock SimulationRepository simulationRepo;
    @InjectMocks AnalyticsService service;

    UUID companyId;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
    }

    @Test
    @DisplayName("getDashboardStats → renvoie totalSimulations/simulationsThisMonth")
    void getDashboardStats_returnsSimulationCounts() {
        when(simulationRepo.countByCompanyId(companyId)).thenReturn(10L);
        when(simulationRepo.countByCompanyIdAndCreatedAtAfter(eq(companyId), any(LocalDateTime.class))).thenReturn(3L);

        DashboardStatsDTO stats = service.getDashboardStats(companyId, "30d");

        assertThat(stats.getTotalSimulations()).isEqualTo(10L);
        assertThat(stats.getSimulationsThisMonth()).isEqualTo(3L);
        assertThat(stats.getPeriod()).isEqualTo("30d");
    }
}
