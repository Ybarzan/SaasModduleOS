package com.incokalk.service.erp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ErpProviderRegistryTest {

    private ErpProviderRegistry registry;

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("getProvider → returns matching provider")
    void getProvider_found() {
        ErpProvider odoo = mock(ErpProvider.class);
        ErpProvider sap = mock(ErpProvider.class);
        when(odoo.getErpType()).thenReturn("ODOO");
        when(sap.getErpType()).thenReturn("SAP");

        registry = new ErpProviderRegistry(List.of(odoo, sap));

        Optional<ErpProvider> result = registry.getProvider("ODOO");
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(odoo);
    }

    @Test
    @DisplayName("getProvider → returns empty when no match")
    void getProvider_notFound() {
        ErpProvider odoo = mock(ErpProvider.class);
        when(odoo.getErpType()).thenReturn("ODOO");

        registry = new ErpProviderRegistry(List.of(odoo));

        Optional<ErpProvider> result = registry.getProvider("SAP");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getProvider → returns empty when no providers")
    void getProvider_noProviders() {
        registry = new ErpProviderRegistry(List.of());

        Optional<ErpProvider> result = registry.getProvider("ODOO");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getProvider → returns first matching provider")
    void getProvider_multipleMatches() {
        ErpProvider odoo1 = mock(ErpProvider.class);
        ErpProvider odoo2 = mock(ErpProvider.class);
        when(odoo1.getErpType()).thenReturn("ODOO");
        when(odoo2.getErpType()).thenReturn("ODOO");

        registry = new ErpProviderRegistry(List.of(odoo1, odoo2));

        Optional<ErpProvider> result = registry.getProvider("ODOO");
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(odoo1);
    }

    @Test
    @DisplayName("getAll → returns unmodifiable list")
    void getAll() {
        ErpProvider odoo = mock(ErpProvider.class);
        ErpProvider sap = mock(ErpProvider.class);
        when(odoo.getErpType()).thenReturn("ODOO");
        when(sap.getErpType()).thenReturn("SAP");

        registry = new ErpProviderRegistry(List.of(odoo, sap));

        List<ErpProvider> result = registry.getAll();
        assertThat(result).hasSize(2);
        assertThatThrownBy(() -> result.add(mock(ErpProvider.class)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("getAll → returns empty when no providers")
    void getAll_empty() {
        registry = new ErpProviderRegistry(List.of());

        List<ErpProvider> result = registry.getAll();
        assertThat(result).isEmpty();
    }
}
