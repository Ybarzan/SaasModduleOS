package com.praxio.service;

import com.praxio.dto.DashboardKeysRequest;
import com.praxio.dto.DashboardKeysStatusDto;
import com.praxio.model.DashboardConfig;
import com.praxio.repository.DashboardConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("DashboardConfigService — Tests unitaires")
class DashboardConfigServiceTest {

    DashboardConfigRepository repository;
    DashboardConfigService service;

    @BeforeEach
    void setUp() {
        repository = mock(DashboardConfigRepository.class);
        service = new DashboardConfigService(repository);
    }

    @Test
    @DisplayName("getStatus → tout désactivé quand aucune configuration n'existe")
    void getStatus_noConfig_allUnconfigured() {
        when(repository.findById(DashboardConfig.SINGLETON_ID)).thenReturn(Optional.empty());

        DashboardKeysStatusDto status = service.getStatus();

        assertThat(status.complianceEngineConfigured()).isFalse();
        assertThat(status.fleetHubConfigured()).isFalse();
        assertThat(status.logistixConfigured()).isFalse();
    }

    @Test
    @DisplayName("updateKeys → crée la ligne singleton si absente et enregistre les clés fournies")
    void updateKeys_createsRowWhenAbsent_setsProvidedKeys() {
        when(repository.findById(DashboardConfig.SINGLETON_ID)).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any(DashboardConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DashboardKeysStatusDto status = service.updateKeys(
                new DashboardKeysRequest("ic_live_abc", "fleethub-key", null, null));

        assertThat(status.complianceEngineConfigured()).isTrue();
        assertThat(status.fleetHubConfigured()).isTrue();
        assertThat(status.logistixConfigured()).isFalse();
        verify(repository).save(org.mockito.ArgumentMatchers.argThat(c ->
                "ic_live_abc".equals(c.getComplianceEngineApiKey())
                        && "fleethub-key".equals(c.getFleetHubApiKey())
                        && c.getLogistixApiKey() == null));
    }

    @Test
    @DisplayName("updateKeys → un champ null laisse la clé existante inchangée")
    void updateKeys_nullField_leavesExistingKeyUnchanged() {
        DashboardConfig existing = DashboardConfig.builder()
                .id(DashboardConfig.SINGLETON_ID)
                .complianceEngineApiKey("existing-key")
                .build();
        when(repository.findById(DashboardConfig.SINGLETON_ID)).thenReturn(Optional.of(existing));
        when(repository.save(org.mockito.ArgumentMatchers.any(DashboardConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.updateKeys(new DashboardKeysRequest(null, "new-fleethub-key", null, null));

        assertThat(existing.getComplianceEngineApiKey()).isEqualTo("existing-key");
        assertThat(existing.getFleetHubApiKey()).isEqualTo("new-fleethub-key");
    }

    @Test
    @DisplayName("updateKeys → une chaîne vide efface la clé existante")
    void updateKeys_blankField_clearsExistingKey() {
        DashboardConfig existing = DashboardConfig.builder()
                .id(DashboardConfig.SINGLETON_ID)
                .complianceEngineApiKey("existing-key")
                .build();
        when(repository.findById(DashboardConfig.SINGLETON_ID)).thenReturn(Optional.of(existing));
        when(repository.save(org.mockito.ArgumentMatchers.any(DashboardConfig.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        DashboardKeysStatusDto status = service.updateKeys(new DashboardKeysRequest("   ", null, null, null));

        assertThat(existing.getComplianceEngineApiKey()).isNull();
        assertThat(status.complianceEngineConfigured()).isFalse();
    }
}
