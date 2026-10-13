package com.praxio.service;

import com.praxio.dto.SsoCredentialsRequest;
import com.praxio.dto.SsoCredentialsStatusDto;
import com.praxio.model.SsoCredentials;
import com.praxio.repository.SsoCredentialsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@DisplayName("SsoCredentialsService — Tests unitaires")
class SsoCredentialsServiceTest {

    SsoCredentialsRepository repository;
    SsoCredentialsService service;

    @BeforeEach
    void setUp() {
        repository = mock(SsoCredentialsRepository.class);
        service = new SsoCredentialsService(repository);
    }

    @Test
    @DisplayName("getStatus → tout désactivé quand aucune configuration n'existe")
    void getStatus_noConfig_allUnconfigured() {
        when(repository.findById(SsoCredentials.SINGLETON_ID)).thenReturn(Optional.empty());

        SsoCredentialsStatusDto status = service.getStatus();

        assertThat(status.complianceEngineConfigured()).isFalse();
        assertThat(status.fleetHubConfigured()).isFalse();
        assertThat(status.logistixConfigured()).isFalse();
        assertThat(status.complianceEngineEmail()).isNull();
    }

    @Test
    @DisplayName("updateCredentials → crée la ligne singleton si absente et enregistre les identifiants fournis")
    void updateCredentials_createsRowWhenAbsent_setsProvidedCredentials() {
        when(repository.findById(SsoCredentials.SINGLETON_ID)).thenReturn(Optional.empty());
        when(repository.save(any(SsoCredentials.class))).thenAnswer(inv -> inv.getArgument(0));

        SsoCredentialsStatusDto status = service.updateCredentials(
                new SsoCredentialsRequest("ops@acme.fr", "secret1", null, null, null, null));

        assertThat(status.complianceEngineConfigured()).isTrue();
        assertThat(status.complianceEngineEmail()).isEqualTo("ops@acme.fr");
        assertThat(status.fleetHubConfigured()).isFalse();
        assertThat(status.logistixConfigured()).isFalse();
        verify(repository).save(argThat(c ->
                "ops@acme.fr".equals(c.getComplianceEngineEmail())
                        && "secret1".equals(c.getComplianceEnginePassword())));
    }

    @Test
    @DisplayName("updateCredentials → email configuré mais mot de passe absent -> pas configuré")
    void updateCredentials_emailWithoutPassword_notConfigured() {
        when(repository.findById(SsoCredentials.SINGLETON_ID)).thenReturn(Optional.empty());
        when(repository.save(any(SsoCredentials.class))).thenAnswer(inv -> inv.getArgument(0));

        SsoCredentialsStatusDto status = service.updateCredentials(
                new SsoCredentialsRequest(null, null, "ops@acme.fr", null, null, null));

        assertThat(status.fleetHubConfigured()).isFalse();
        assertThat(status.fleetHubEmail()).isEqualTo("ops@acme.fr");
    }

    @Test
    @DisplayName("updateCredentials → un champ null laisse la valeur existante inchangée")
    void updateCredentials_nullField_leavesExistingValueUnchanged() {
        SsoCredentials existing = SsoCredentials.builder()
                .id(SsoCredentials.SINGLETON_ID)
                .complianceEngineEmail("existing@acme.fr")
                .complianceEnginePassword("existing-pass")
                .build();
        when(repository.findById(SsoCredentials.SINGLETON_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any(SsoCredentials.class))).thenAnswer(inv -> inv.getArgument(0));

        service.updateCredentials(new SsoCredentialsRequest(null, null, "fh@acme.fr", "fh-pass", null, null));

        assertThat(existing.getComplianceEngineEmail()).isEqualTo("existing@acme.fr");
        assertThat(existing.getComplianceEnginePassword()).isEqualTo("existing-pass");
        assertThat(existing.getFleetHubEmail()).isEqualTo("fh@acme.fr");
    }

    @Test
    @DisplayName("updateCredentials → une chaîne vide efface la valeur existante")
    void updateCredentials_blankField_clearsExistingValue() {
        SsoCredentials existing = SsoCredentials.builder()
                .id(SsoCredentials.SINGLETON_ID)
                .complianceEngineEmail("existing@acme.fr")
                .complianceEnginePassword("existing-pass")
                .build();
        when(repository.findById(SsoCredentials.SINGLETON_ID)).thenReturn(Optional.of(existing));
        when(repository.save(any(SsoCredentials.class))).thenAnswer(inv -> inv.getArgument(0));

        SsoCredentialsStatusDto status = service.updateCredentials(
                new SsoCredentialsRequest("   ", "   ", null, null, null, null));

        assertThat(existing.getComplianceEngineEmail()).isNull();
        assertThat(existing.getComplianceEnginePassword()).isNull();
        assertThat(status.complianceEngineConfigured()).isFalse();
    }
}
