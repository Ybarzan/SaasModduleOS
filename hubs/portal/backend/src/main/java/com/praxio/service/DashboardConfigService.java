package com.praxio.service;

import com.praxio.dto.DashboardKeysRequest;
import com.praxio.dto.DashboardKeysStatusDto;
import com.praxio.model.DashboardConfig;
import com.praxio.repository.DashboardConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Praxio n'est pas multi-tenant : une seule ligne de configuration
 * ({@link DashboardConfig#SINGLETON_ID}), créée à la première écriture.
 */
@Service
@RequiredArgsConstructor
public class DashboardConfigService {

    private final DashboardConfigRepository repository;

    @Transactional(readOnly = true)
    public DashboardKeysStatusDto getStatus() {
        DashboardConfig config = repository.findById(DashboardConfig.SINGLETON_ID).orElse(null);
        return config == null ? new DashboardKeysStatusDto(false, false, false) : toStatus(config);
    }

    @Transactional
    public DashboardKeysStatusDto updateKeys(DashboardKeysRequest request) {
        DashboardConfig config = repository.findById(DashboardConfig.SINGLETON_ID)
                .orElseGet(() -> DashboardConfig.builder().id(DashboardConfig.SINGLETON_ID).build());

        if (request.complianceEngineApiKey() != null) {
            config.setComplianceEngineApiKey(blankToNull(request.complianceEngineApiKey()));
        }
        if (request.fleetHubApiKey() != null) {
            config.setFleetHubApiKey(blankToNull(request.fleetHubApiKey()));
        }
        if (request.logistixApiKey() != null) {
            config.setLogistixApiKey(blankToNull(request.logistixApiKey()));
        }
        DashboardConfig saved = repository.save(config);
        return toStatus(saved);
    }

    private DashboardKeysStatusDto toStatus(DashboardConfig config) {
        return new DashboardKeysStatusDto(
                isConfigured(config.getComplianceEngineApiKey()),
                isConfigured(config.getFleetHubApiKey()),
                isConfigured(config.getLogistixApiKey()));
    }

    /** Lecture interne pour l'agrégation cross-hub -- jamais exposée via l'API. */
    @Transactional(readOnly = true)
    public DashboardConfig getConfigOrEmpty() {
        return repository.findById(DashboardConfig.SINGLETON_ID)
                .orElseGet(() -> DashboardConfig.builder().id(DashboardConfig.SINGLETON_ID).build());
    }

    private boolean isConfigured(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
