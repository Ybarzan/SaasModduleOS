package com.incokalk.service.fleethub;

import com.incokalk.dto.config.FleetHubConfigDTO;
import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Company;
import com.incokalk.model.FleetHubConfig;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.FleetHubConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FleetHubConfigService {

    private final FleetHubConfigRepository configRepo;
    private final CompanyRepository companyRepo;
    private final FleetHubClient client;

    public List<FleetHubConfig> listConfigs(UUID companyId) {
        return configRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    @Transactional
    public FleetHubConfig createConfig(FleetHubConfigDTO dto, UUID companyId) {
        if (dto.getPassword() == null || dto.getPassword().isBlank()) {
            throw new IllegalArgumentException("Le mot de passe est obligatoire à la création");
        }
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée"));

        FleetHubConfig config = FleetHubConfig.builder()
                .company(company)
                .name(dto.getName())
                .baseUrl(dto.getBaseUrl())
                .username(dto.getUsername())
                .password(dto.getPassword())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();

        return configRepo.save(config);
    }

    @Transactional
    public FleetHubConfig updateConfig(UUID id, FleetHubConfigDTO dto, UUID companyId) {
        FleetHubConfig config = getOwnedConfig(id, companyId);

        if (dto.getName() != null) config.setName(dto.getName());
        if (dto.getBaseUrl() != null) config.setBaseUrl(dto.getBaseUrl());
        if (dto.getUsername() != null) config.setUsername(dto.getUsername());
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) config.setPassword(dto.getPassword());
        if (dto.getIsActive() != null) config.setIsActive(dto.getIsActive());

        return configRepo.save(config);
    }

    @Transactional
    public void deleteConfig(UUID id, UUID companyId) {
        configRepo.delete(getOwnedConfig(id, companyId));
    }

    @Transactional
    public boolean testConnection(UUID id, UUID companyId) {
        FleetHubConfig config = getOwnedConfig(id, companyId);
        try {
            client.getVehicles(config);
            config.setLastError(null);
            configRepo.save(config);
            return true;
        } catch (Exception e) {
            log.warn("[FleetHub] Échec test connexion {}: {}", config.getId(), e.getMessage());
            config.setLastError(e.getMessage());
            configRepo.save(config);
            return false;
        }
    }

    /** Liste des véhicules de la flotte, avec leur position GPS courante --
     * utilisée par l'UI pour confirmer que l'intégration fonctionne et pour
     * peupler un sélecteur de camion lors de l'assignation à une expédition. */
    public List<FleetHubVehicle> listVehicles(UUID id, UUID companyId) {
        FleetHubConfig config = getOwnedConfig(id, companyId);
        return client.getVehicles(config);
    }

    private FleetHubConfig getOwnedConfig(UUID id, UUID companyId) {
        return configRepo.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Configuration fleet-hub non trouvée"));
    }
}
