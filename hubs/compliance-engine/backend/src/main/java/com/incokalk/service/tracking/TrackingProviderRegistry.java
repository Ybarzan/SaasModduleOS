package com.incokalk.service.tracking;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Slf4j
@Component
public class TrackingProviderRegistry {

    private final Map<String, TrackingProvider> providers = new LinkedHashMap<>();

    @Autowired
    public TrackingProviderRegistry(List<TrackingProvider> providerList) {
        for (TrackingProvider provider : providerList) {
            providers.put(provider.getProviderType(), provider);
            log.info("Registered tracking provider: {} ({})", provider.getName(), provider.getProviderType());
        }
    }

    public TrackingProvider getProvider(String type) {
        if (type == null) return null;
        return providers.get(type.toUpperCase());
    }

    public List<TrackingProvider> getAllProviders() {
        return new ArrayList<>(providers.values());
    }

    public List<TrackingProvider> getAvailableProviders() {
        return providers.values().stream()
                .filter(p -> p.isAvailable(null))
                .toList();
    }
}
