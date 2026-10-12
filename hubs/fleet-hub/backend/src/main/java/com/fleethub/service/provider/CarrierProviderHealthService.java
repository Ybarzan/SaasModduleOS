package com.fleethub.service.provider;

import com.fleethub.repository.CarrierProviderConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Circuit-breaker léger pour les fournisseurs de devis transport (DHL, Shippo, …).
 * Équivalent local, scopé au seul domaine Transport, du {@code ProviderHealthService}
 * générique de compliance-engine — volontairement non importé, voir
 * {@link com.fleethub.model.CarrierProviderConfig}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CarrierProviderHealthService {

    private final CarrierProviderConfigRepository providerConfigRepo;

    @Transactional
    public void recordSuccess(String providerType, Long companyId) {
        providerConfigRepo.findByCompanyIdAndProviderType(companyId, providerType).ifPresent(pc -> {
            pc.setConsecutiveFailures(0);
            pc.setHealthStatus("HEALTHY");
            pc.setLastHealthCheck(LocalDateTime.now());
            providerConfigRepo.save(pc);
            log.debug("[Health] {} marqué HEALTHY pour {}", providerType, companyId);
        });
    }

    @Transactional
    public void recordFailure(String providerType, Long companyId) {
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

    public boolean isCircuitBroken(String providerType, Long companyId) {
        return providerConfigRepo.findByCompanyIdAndProviderType(companyId, providerType)
                .map(pc -> pc.getConsecutiveFailures() >= 5)
                .orElse(true);
    }
}
