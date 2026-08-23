package com.incokalk.service;

import com.incokalk.dto.config.ProviderConfigDTO;
import com.incokalk.dto.config.ProviderHealthDTO;
import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Company;
import com.incokalk.model.ProviderConfig;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.ProviderConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProviderHealthService {

    private final ProviderConfigRepository providerConfigRepo;
    private final CompanyRepository companyRepo;

    @Transactional
    public void recordSuccess(String providerType, UUID companyId) {
        providerConfigRepo.findByCompanyIdAndProviderType(companyId, providerType).ifPresent(pc -> {
            pc.setConsecutiveFailures(0);
            pc.setHealthStatus("HEALTHY");
            pc.setLastHealthCheck(LocalDateTime.now());
            providerConfigRepo.save(pc);
            log.debug("[Health] {} marqué HEALTHY pour {}", providerType, companyId);
        });
    }

    @Transactional
    public void recordFailure(String providerType, UUID companyId) {
        providerConfigRepo.findByCompanyIdAndProviderType(companyId, providerType).ifPresent(pc -> {
            int failures = pc.getConsecutiveFailures() + 1;
            pc.setConsecutiveFailures(failures);
            if (failures >= 3) {
                pc.setHealthStatus("DOWN");
            } else if (failures >= 1) {
                pc.setHealthStatus("DEGRADED");
            }
            pc.setLastHealthCheck(LocalDateTime.now());
            providerConfigRepo.save(pc);
            log.warn("[Health] {} marqué {} ({} échecs consécutifs) pour {}", providerType, pc.getHealthStatus(), failures, companyId);
        });
    }

    public List<ProviderHealthDTO> getHealth(UUID companyId) {
        return providerConfigRepo.findByCompanyIdOrderByPriorityAsc(companyId).stream()
                .map(pc -> ProviderHealthDTO.builder()
                        .providerType(pc.getProviderType())
                        .healthStatus(pc.getHealthStatus())
                        .lastHealthCheck(pc.getLastHealthCheck())
                        .consecutiveFailures(pc.getConsecutiveFailures())
                        .isActive(pc.isActive())
                        .build())
                .toList();
    }

    public List<ProviderConfig> listProviderConfigs(UUID companyId) {
        return providerConfigRepo.findByCompanyIdOrderByPriorityAsc(companyId);
    }

    public Optional<ProviderConfig> findProviderConfigById(UUID id, UUID companyId) {
        return providerConfigRepo.findById(id)
            .filter(c -> c.getCompany().getId().equals(companyId));
    }

    @Transactional
    public ProviderConfig createOrUpdateProviderConfig(ProviderConfigDTO dto, UUID companyId) {
        Company company = companyRepo.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée"));

        ProviderConfig config = providerConfigRepo.findByCompanyIdAndProviderType(companyId, dto.getProviderType())
            .orElse(ProviderConfig.builder()
                .company(company)
                .providerType(dto.getProviderType())
                .build());

        if (dto.getApiKey() != null) config.setApiKeyEncrypted(dto.getApiKey());
        if (dto.getApiSecret() != null) config.setApiSecret(dto.getApiSecret());
        if (dto.getPriority() != null) config.setPriority(dto.getPriority());
        if (dto.getIsActive() != null) config.setActive(dto.getIsActive());
        if (dto.getConfigJson() != null) config.setConfigJson(dto.getConfigJson());
        config.setUpdatedAt(LocalDateTime.now());

        return providerConfigRepo.save(config);
    }

    @Transactional
    public void deleteProviderConfig(UUID id, UUID companyId) {
        ProviderConfig config = findProviderConfigById(id, companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Configuration non trouvée"));
        providerConfigRepo.delete(config);
    }

    public boolean isCircuitBroken(String providerType, UUID companyId) {
        return providerConfigRepo.findByCompanyIdAndProviderType(companyId, providerType)
                .map(pc -> pc.getConsecutiveFailures() >= 5)
                .orElse(true);
    }
}
