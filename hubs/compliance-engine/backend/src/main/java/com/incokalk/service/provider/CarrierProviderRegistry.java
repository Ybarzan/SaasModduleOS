package com.incokalk.service.provider;

import com.incokalk.model.ProviderConfig;
import com.incokalk.repository.ProviderConfigRepository;
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
    private final ProviderConfigRepository providerConfigRepo;

    public List<CarrierProvider> getProviders(UUID companyId) {
        Set<String> activeTypes = providerConfigRepo.findByCompanyIdAndIsActiveTrueOrderByPriorityAsc(companyId)
                .stream()
                .map(ProviderConfig::getProviderType)
                .collect(Collectors.toSet());

        return providers.stream()
                .filter(p -> activeTypes.contains(p.getProviderType()))
                .sorted(Comparator.comparingInt(p -> {
                    return providerConfigRepo.findByCompanyIdAndProviderType(companyId, p.getProviderType())
                            .map(ProviderConfig::getPriority)
                            .orElse(Integer.MAX_VALUE);
                }))
                .toList();
    }

    public Optional<CarrierProvider> getProvider(String type, UUID companyId) {
        return getProviders(companyId).stream()
                .filter(p -> p.getProviderType().equals(type))
                .findFirst();
    }

    public List<CarrierProvider> getAllAvailableProviders(UUID companyId) {
        return getProviders(companyId).stream()
                .filter(p -> p.isAvailable(companyId))
                .toList();
    }

    public List<CarrierProvider> getAll() {
        return Collections.unmodifiableList(providers);
    }
}
