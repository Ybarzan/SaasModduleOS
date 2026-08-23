package com.incokalk.service.fintech;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class FintechProviderRegistry {

    private final List<FintechAdapter> adapters;

    public Optional<FintechAdapter> getAdapter(String providerType) {
        return adapters.stream()
            .filter(a -> a.getProviderType().equalsIgnoreCase(providerType))
            .findFirst();
    }

    public List<FintechAdapter> getAll() {
        return Collections.unmodifiableList(adapters);
    }
}
