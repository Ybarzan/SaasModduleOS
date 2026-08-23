package com.incokalk.service;

import com.incokalk.model.ProviderConfig;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.ProviderConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ProviderHealthService — Tests unitaires")
class ProviderHealthServiceTest {

    ProviderConfigRepository repo;
    CompanyRepository companyRepo;
    ProviderHealthService service;
    UUID companyId;

    @BeforeEach
    void setUp() {
        repo = mock(ProviderConfigRepository.class);
        companyRepo = mock(CompanyRepository.class);
        service = new ProviderHealthService(repo, companyRepo);
        companyId = UUID.randomUUID();
    }

    @Test
    @DisplayName("recordSuccess → reset échecs, HEALTHY")
    void recordSuccess() {
        ProviderConfig pc = new ProviderConfig();
        pc.setConsecutiveFailures(3);
        pc.setHealthStatus("DOWN");
        when(repo.findByCompanyIdAndProviderType(companyId, "DHL")).thenReturn(Optional.of(pc));

        service.recordSuccess("DHL", companyId);

        assertThat(pc.getConsecutiveFailures()).isZero();
        assertThat(pc.getHealthStatus()).isEqualTo("HEALTHY");
        verify(repo).save(pc);
    }

    @Test
    @DisplayName("recordSuccess → pas de config → no-op")
    void recordSuccess_noConfig() {
        when(repo.findByCompanyIdAndProviderType(companyId, "DHL")).thenReturn(Optional.empty());
        service.recordSuccess("DHL", companyId);
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("recordFailure → 1 échec → DEGRADED")
    void recordFailure_degraded() {
        ProviderConfig pc = new ProviderConfig();
        pc.setConsecutiveFailures(0);
        pc.setHealthStatus("HEALTHY");
        when(repo.findByCompanyIdAndProviderType(companyId, "DHL")).thenReturn(Optional.of(pc));

        service.recordFailure("DHL", companyId);

        assertThat(pc.getConsecutiveFailures()).isEqualTo(1);
        assertThat(pc.getHealthStatus()).isEqualTo("DEGRADED");
    }

    @Test
    @DisplayName("recordFailure → 3 échecs → DOWN")
    void recordFailure_down() {
        ProviderConfig pc = new ProviderConfig();
        pc.setConsecutiveFailures(2);
        pc.setHealthStatus("DEGRADED");
        when(repo.findByCompanyIdAndProviderType(companyId, "DHL")).thenReturn(Optional.of(pc));

        service.recordFailure("DHL", companyId);

        assertThat(pc.getConsecutiveFailures()).isEqualTo(3);
        assertThat(pc.getHealthStatus()).isEqualTo("DOWN");
    }

    @Test
    @DisplayName("getHealth → liste ordonnée par priorité")
    void getHealth() {
        ProviderConfig pc1 = new ProviderConfig();
        pc1.setProviderType("DHL");
        pc1.setHealthStatus("HEALTHY");
        when(repo.findByCompanyIdOrderByPriorityAsc(companyId)).thenReturn(List.of(pc1));

        List<?> result = service.getHealth(companyId);
        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("isCircuitBroken → 5+ échecs → true")
    void isCircuitBroken_true() {
        ProviderConfig pc = new ProviderConfig();
        pc.setConsecutiveFailures(5);
        when(repo.findByCompanyIdAndProviderType(companyId, "DHL")).thenReturn(Optional.of(pc));
        assertThat(service.isCircuitBroken("DHL", companyId)).isTrue();
    }

    @Test
    @DisplayName("isCircuitBroken → < 5 échecs → false")
    void isCircuitBroken_false() {
        ProviderConfig pc = new ProviderConfig();
        pc.setConsecutiveFailures(3);
        when(repo.findByCompanyIdAndProviderType(companyId, "DHL")).thenReturn(Optional.of(pc));
        assertThat(service.isCircuitBroken("DHL", companyId)).isFalse();
    }

    @Test
    @DisplayName("isCircuitBroken → pas de config → true (circuit ouvert)")
    void isCircuitBroken_noConfig() {
        when(repo.findByCompanyIdAndProviderType(companyId, "DHL")).thenReturn(Optional.empty());
        assertThat(service.isCircuitBroken("DHL", companyId)).isTrue();
    }
}
