package com.incokalk.service.fleethub;

import com.incokalk.dto.config.FleetHubConfigDTO;
import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Company;
import com.incokalk.model.FleetHubConfig;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.FleetHubConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("FleetHubConfigService — Tests unitaires")
class FleetHubConfigServiceTest {

    @Mock FleetHubConfigRepository configRepo;
    @Mock CompanyRepository companyRepo;
    @Mock FleetHubClient client;

    private FleetHubConfigService service;
    private UUID companyId, configId;
    private Company company;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new FleetHubConfigService(configRepo, companyRepo, client);
        companyId = UUID.randomUUID();
        configId = UUID.randomUUID();
        company = Company.builder().id(companyId).build();
    }

    private FleetHubConfigDTO dto(String password) {
        return FleetHubConfigDTO.builder()
                .name("Flotte principale").baseUrl("https://fleethub.example.com")
                .username("integration@acme.io").password(password).isActive(true)
                .build();
    }

    @Test
    @DisplayName("createConfig : mot de passe manquant -> IllegalArgumentException")
    void createConfig_missingPassword_throws() {
        assertThatThrownBy(() -> service.createConfig(dto(null), companyId))
                .isInstanceOf(IllegalArgumentException.class);
        verify(configRepo, never()).save(any());
    }

    @Test
    @DisplayName("createConfig : entreprise introuvable -> ResourceNotFoundException")
    void createConfig_companyNotFound_throws() {
        when(companyRepo.findById(companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createConfig(dto("secret"), companyId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("createConfig : cas nominal -> sauvegarde avec le mot de passe fourni")
    void createConfig_success_savesConfig() {
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(configRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        FleetHubConfig result = service.createConfig(dto("secret"), companyId);

        assertThat(result.getName()).isEqualTo("Flotte principale");
        assertThat(result.getPassword()).isEqualTo("secret");
        assertThat(result.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("updateConfig : mot de passe non fourni -> conserve l'ancien")
    void updateConfig_noPassword_keepsExisting() {
        FleetHubConfig existing = FleetHubConfig.builder().id(configId).company(company)
                .name("Ancien nom").baseUrl("https://old.example.com").username("old@acme.io")
                .password("ancien-secret").isActive(true).build();
        when(configRepo.findByIdAndCompanyId(configId, companyId)).thenReturn(Optional.of(existing));
        when(configRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        FleetHubConfig result = service.updateConfig(configId, dto(null), companyId);

        assertThat(result.getPassword()).isEqualTo("ancien-secret");
        assertThat(result.getName()).isEqualTo("Flotte principale");
    }

    @Test
    @DisplayName("updateConfig : mot de passe fourni -> remplace l'ancien")
    void updateConfig_withPassword_replacesExisting() {
        FleetHubConfig existing = FleetHubConfig.builder().id(configId).company(company)
                .name("Ancien nom").password("ancien-secret").isActive(true).build();
        when(configRepo.findByIdAndCompanyId(configId, companyId)).thenReturn(Optional.of(existing));
        when(configRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        FleetHubConfig result = service.updateConfig(configId, dto("nouveau-secret"), companyId);

        assertThat(result.getPassword()).isEqualTo("nouveau-secret");
    }

    @Test
    @DisplayName("updateConfig : config d'une autre entreprise -> ResourceNotFoundException")
    void updateConfig_wrongCompany_throws() {
        when(configRepo.findByIdAndCompanyId(configId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateConfig(configId, dto("secret"), companyId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("deleteConfig : supprime la config appartenant à l'entreprise")
    void deleteConfig_success_deletes() {
        FleetHubConfig existing = FleetHubConfig.builder().id(configId).company(company).build();
        when(configRepo.findByIdAndCompanyId(configId, companyId)).thenReturn(Optional.of(existing));

        service.deleteConfig(configId, companyId);

        verify(configRepo).delete(existing);
    }

    @Test
    @DisplayName("testConnection : succès -> true, efface la dernière erreur")
    void testConnection_success_clearsLastError() {
        FleetHubConfig existing = FleetHubConfig.builder().id(configId).company(company).lastError("ancienne erreur").build();
        when(configRepo.findByIdAndCompanyId(configId, companyId)).thenReturn(Optional.of(existing));
        when(client.getVehicles(existing)).thenReturn(List.of());
        when(configRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        boolean result = service.testConnection(configId, companyId);

        assertThat(result).isTrue();
        assertThat(existing.getLastError()).isNull();
    }

    @Test
    @DisplayName("testConnection : échec -> false, enregistre le message d'erreur")
    void testConnection_failure_recordsError() {
        FleetHubConfig existing = FleetHubConfig.builder().id(configId).company(company).build();
        when(configRepo.findByIdAndCompanyId(configId, companyId)).thenReturn(Optional.of(existing));
        when(client.getVehicles(existing)).thenThrow(new FleetHubClient.FleetHubException("Connexion refusée"));
        when(configRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        boolean result = service.testConnection(configId, companyId);

        assertThat(result).isFalse();
        assertThat(existing.getLastError()).isEqualTo("Connexion refusée");
    }

    @Test
    @DisplayName("listVehicles : délègue au client pour la config appartenant à l'entreprise")
    void listVehicles_delegatesToClient() {
        FleetHubConfig existing = FleetHubConfig.builder().id(configId).company(company).build();
        when(configRepo.findByIdAndCompanyId(configId, companyId)).thenReturn(Optional.of(existing));
        FleetHubVehicle vehicle = FleetHubVehicle.builder().registration("AB-123-CD").build();
        when(client.getVehicles(existing)).thenReturn(List.of(vehicle));

        List<FleetHubVehicle> result = service.listVehicles(configId, companyId);

        assertThat(result).containsExactly(vehicle);
    }

    @Test
    @DisplayName("listVehicles : config d'une autre entreprise -> ResourceNotFoundException")
    void listVehicles_wrongCompany_throws() {
        when(configRepo.findByIdAndCompanyId(configId, companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listVehicles(configId, companyId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("listAllActiveVehicles : agrège les véhicules de toutes les configs actives")
    void listAllActiveVehicles_aggregatesAcrossConfigs() {
        FleetHubConfig first = FleetHubConfig.builder().id(UUID.randomUUID()).company(company).isActive(true).build();
        FleetHubConfig second = FleetHubConfig.builder().id(UUID.randomUUID()).company(company).isActive(true).build();
        when(configRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of(first, second));
        FleetHubVehicle v1 = FleetHubVehicle.builder().registration("AB-123-CD").build();
        FleetHubVehicle v2 = FleetHubVehicle.builder().registration("EF-456-GH").build();
        when(client.getVehicles(first)).thenReturn(List.of(v1));
        when(client.getVehicles(second)).thenReturn(List.of(v2));

        List<FleetHubVehicle> result = service.listAllActiveVehicles(companyId);

        assertThat(result).containsExactlyInAnyOrder(v1, v2);
    }

    @Test
    @DisplayName("listAllActiveVehicles : une config en échec n'empêche pas de renvoyer les véhicules des autres")
    void listAllActiveVehicles_oneConfigFails_returnsOthers() {
        FleetHubConfig first = FleetHubConfig.builder().id(UUID.randomUUID()).company(company).isActive(true).build();
        FleetHubConfig second = FleetHubConfig.builder().id(UUID.randomUUID()).company(company).isActive(true).build();
        when(configRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of(first, second));
        when(client.getVehicles(first)).thenThrow(new FleetHubClient.FleetHubException("Connexion refusée"));
        FleetHubVehicle v2 = FleetHubVehicle.builder().registration("EF-456-GH").build();
        when(client.getVehicles(second)).thenReturn(List.of(v2));

        List<FleetHubVehicle> result = service.listAllActiveVehicles(companyId);

        assertThat(result).containsExactly(v2);
    }

    @Test
    @DisplayName("listAllActiveVehicles : aucune config active -> liste vide")
    void listAllActiveVehicles_noActiveConfig_empty() {
        when(configRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of());

        assertThat(service.listAllActiveVehicles(companyId)).isEmpty();
    }
}
