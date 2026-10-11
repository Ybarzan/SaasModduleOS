package com.fleethub.service.provider;

import com.fleethub.model.CarrierProviderConfig;
import com.fleethub.repository.CarrierProviderConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class CarrierProviderRegistry {

    private final List<CarrierProvider> providers;
    private final CarrierProviderConfigRepository providerConfigRepo;

    public List<CarrierProvider> getProviders(Long companyId) {
        Set<String> activeTypes = providerConfigRepo.findByCompanyIdAndIsActiveTrueOrderByPriorityAsc(companyId)
                .stream()
                .map(CarrierProviderConfig::getProviderType)
                .collect(Collectors.toSet());

        return providers.stream()
                .filter(p -> activeTypes.contains(p.getProviderType()))
                .sorted(Comparator.comparingInt(p -> {
                    return providerConfigRepo.findByCompanyIdAndProviderType(companyId, p.getProviderType())
                            .map(CarrierProviderConfig::getPriority)
                            .orElse(Integer.MAX_VALUE);
                }))
                .toList();
    }

    public Optional<CarrierProvider> getProvider(String type, Long companyId) {
        return getProviders(companyId).stream()
                .filter(p -> p.getProviderType().equals(type))
                .findFirst();
    }

    public List<CarrierProvider> getAllAvailableProviders(Long companyId) {
        return getProviders(companyId).stream()
                .filter(p -> p.isAvailable(companyId))
                .toList();
    }

    public List<CarrierProvider> getAll() {
        return Collections.unmodifiableList(providers);
    }
}
