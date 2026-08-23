package com.incokalk.service.erp;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class ErpProviderRegistry {

    private final List<ErpProvider> providers;

    public Optional<ErpProvider> getProvider(String type) {
        return providers.stream()
                .filter(p -> p.getErpType().equals(type))
                .findFirst();
    }

    public List<ErpProvider> getAll() {
        return Collections.unmodifiableList(providers);
    }
}
