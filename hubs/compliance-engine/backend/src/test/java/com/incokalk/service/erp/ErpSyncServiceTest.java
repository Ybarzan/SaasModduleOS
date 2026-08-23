package com.incokalk.service.erp;

import com.incokalk.dto.config.ErpConfigDTO;
import com.incokalk.dto.config.ErpHealthDTO;
import com.incokalk.dto.config.ErpSyncLogDTO;
import com.incokalk.dto.shared.SyncRequestDTO;
import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Company;
import com.incokalk.model.ErpConfig;
import com.incokalk.model.ErpSyncLog;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.ErpConfigRepository;
import com.incokalk.repository.ErpSyncLogRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ErpSyncServiceTest {

    private ErpConfigRepository erpConfigRepo;
    private ErpSyncLogRepository erpSyncLogRepo;
    private CompanyRepository companyRepo;
    private ShipmentOrderRepository shipmentOrderRepo;
    private ErpProviderRegistry providerRegistry;
    private ErpSyncService service;

    @BeforeEach
    void setUp() {
        erpConfigRepo = mock(ErpConfigRepository.class);
        erpSyncLogRepo = mock(ErpSyncLogRepository.class);
        companyRepo = mock(CompanyRepository.class);
        shipmentOrderRepo = mock(ShipmentOrderRepository.class);
        providerRegistry = mock(ErpProviderRegistry.class);
        service = new ErpSyncService(erpConfigRepo, erpSyncLogRepo, companyRepo, shipmentOrderRepo, providerRegistry);
    }

    private ErpConfig config(UUID companyId, String erpType) {
        return ErpConfig.builder()
                .id(UUID.randomUUID())
                .company(Company.builder().id(companyId).build())
                .erpType(erpType)
                .name("Test ERP")
                .apiEndpoint("https://api.example.com")
                .apiKey("key")
                .apiSecret("secret")
                .databaseName("db")
                .username("user")
                .isActive(true)
                .syncStatus("IDLE")
                .build();
    }

    @Test
    @DisplayName("listConfigs → returns from repo")
    void listConfigs() {
        UUID companyId = UUID.randomUUID();
        var config1 = config(companyId, "ODOO");
        var config2 = config(companyId, "SAP");
        when(erpConfigRepo.findByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(config1, config2));

        List<ErpConfig> result = service.listConfigs(companyId);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getErpType()).isEqualTo("ODOO");
    }

    @Test
    @DisplayName("createConfig → success")
    void createConfig_success() {
        UUID companyId = UUID.randomUUID();
        Company company = Company.builder().id(companyId).build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(erpConfigRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ErpConfigDTO dto = ErpConfigDTO.builder()
                .erpType("odoo")
                .name("My Odoo")
                .apiEndpoint("https://odoo.example.com")
                .apiKey("key123")
                .apiSecret("secret456")
                .databaseName("mydb")
                .username("admin")
                .isActive(true)
                .configJson("{extra: true}")
                .build();

        ErpConfig result = service.createConfig(dto, companyId);
        assertThat(result.getCompany()).isEqualTo(company);
        assertThat(result.getErpType()).isEqualTo("ODOO");
        assertThat(result.getName()).isEqualTo("My Odoo");
        assertThat(result.getApiEndpoint()).isEqualTo("https://odoo.example.com");
        assertThat(result.getApiKey()).isEqualTo("key123");
        assertThat(result.getApiSecret()).isEqualTo("secret456");
        assertThat(result.getDatabaseName()).isEqualTo("mydb");
        assertThat(result.getUsername()).isEqualTo("admin");
        assertThat(result.getIsActive()).isTrue();
        assertThat(result.getConfigJson()).isEqualTo("{extra: true}");
        assertThat(result.getSyncStatus()).isEqualTo("IDLE");
    }

    @Test
    @DisplayName("createConfig → company not found throws")
    void createConfig_companyNotFound() {
        UUID companyId = UUID.randomUUID();
        when(companyRepo.findById(companyId)).thenReturn(Optional.empty());

        ErpConfigDTO dto = ErpConfigDTO.builder().erpType("ODOO").name("Test").build();
        assertThatThrownBy(() -> service.createConfig(dto, companyId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Entreprise non trouvée");
    }

    @Test
    @DisplayName("createConfig → default isActive when null")
    void createConfig_defaultIsActive() {
        UUID companyId = UUID.randomUUID();
        Company company = Company.builder().id(companyId).build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(erpConfigRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ErpConfigDTO dto = ErpConfigDTO.builder()
                .erpType("ODOO")
                .name("Test")
                .isActive(null)
                .build();

        ErpConfig result = service.createConfig(dto, companyId);
        assertThat(result.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("createConfig → default sync frequency")
    void createConfig_defaultSyncFrequency() {
        UUID companyId = UUID.randomUUID();
        Company company = Company.builder().id(companyId).build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(erpConfigRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ErpConfigDTO dto = ErpConfigDTO.builder()
                .erpType("ODOO")
                .name("Test")
                .build();

        ErpConfig result = service.createConfig(dto, companyId);
        assertThat(result.getSyncStatus()).isEqualTo("IDLE");
    }

    @Test
    @DisplayName("updateConfig → success")
    void updateConfig_success() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));
        when(erpConfigRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ErpConfigDTO dto = ErpConfigDTO.builder()
                .name("Updated Name")
                .apiEndpoint("https://updated.example.com")
                .apiKey("new-key")
                .apiSecret("new-secret")
                .databaseName("new-db")
                .username("new-user")
                .isActive(false)
                .configJson("{new: true}")
                .erpType("sap")
                .build();

        ErpConfig result = service.updateConfig(configId, dto, companyId);
        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getApiEndpoint()).isEqualTo("https://updated.example.com");
        assertThat(result.getApiKey()).isEqualTo("new-key");
        assertThat(result.getApiSecret()).isEqualTo("new-secret");
        assertThat(result.getDatabaseName()).isEqualTo("new-db");
        assertThat(result.getUsername()).isEqualTo("new-user");
        assertThat(result.getIsActive()).isFalse();
        assertThat(result.getConfigJson()).isEqualTo("{new: true}");
        assertThat(result.getErpType()).isEqualTo("SAP");
    }

    @Test
    @DisplayName("updateConfig → not found throws")
    void updateConfig_notFound() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.empty());

        ErpConfigDTO dto = ErpConfigDTO.builder().name("Test").build();
        assertThatThrownBy(() -> service.updateConfig(configId, dto, companyId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Configuration ERP non trouvée");
    }

    @Test
    @DisplayName("updateConfig → wrong company throws")
    void updateConfig_wrongCompany() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(UUID.randomUUID(), "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));

        ErpConfigDTO dto = ErpConfigDTO.builder().name("Test").build();
        assertThatThrownBy(() -> service.updateConfig(configId, dto, companyId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("updateConfig → partial update (null fields ignored)")
    void updateConfig_partial() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        config.setName("Original");
        config.setApiEndpoint("https://original.com");
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));
        when(erpConfigRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ErpConfigDTO dto = ErpConfigDTO.builder()
                .name("Updated Name")
                .build();

        ErpConfig result = service.updateConfig(configId, dto, companyId);
        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getApiEndpoint()).isEqualTo("https://original.com");
    }

    @Test
    @DisplayName("deleteConfig → success")
    void deleteConfig_success() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));

        service.deleteConfig(configId, companyId);
        verify(erpConfigRepo).delete(config);
    }

    @Test
    @DisplayName("deleteConfig → not found throws")
    void deleteConfig_notFound() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteConfig(configId, companyId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("deleteConfig → wrong company throws")
    void deleteConfig_wrongCompany() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(UUID.randomUUID(), "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));

        assertThatThrownBy(() -> service.deleteConfig(configId, companyId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("testConnection → provider found and returns true")
    void testConnection_success() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));

        ErpProvider provider = mock(ErpProvider.class);
        when(provider.testConnection(config)).thenReturn(true);
        when(providerRegistry.getProvider("ODOO")).thenReturn(Optional.of(provider));

        boolean result = service.testConnection(configId, companyId);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("testConnection → provider not found returns false")
    void testConnection_noProvider() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));
        when(providerRegistry.getProvider("ODOO")).thenReturn(Optional.empty());

        boolean result = service.testConnection(configId, companyId);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("testConnection → exception returns false")
    void testConnection_exception() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));

        ErpProvider provider = mock(ErpProvider.class);
        when(provider.testConnection(config)).thenThrow(new RuntimeException("Connection failed"));
        when(providerRegistry.getProvider("ODOO")).thenReturn(Optional.of(provider));

        boolean result = service.testConnection(configId, companyId);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("testConnection → not found throws")
    void testConnection_notFound() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.testConnection(configId, companyId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("sync → success with PRODUCTS IMPORT")
    void sync_productsImport_success() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));
        when(erpSyncLogRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(erpConfigRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ErpProvider provider = mock(ErpProvider.class);
        ErpSyncResult syncResult = ErpSyncResult.builder()
                .success(true)
                .recordsTotal(10)
                .recordsSynced(10)
                .recordsFailed(0)
                .build();
        when(provider.importProducts(config)).thenReturn(syncResult);
        when(providerRegistry.getProvider("ODOO")).thenReturn(Optional.of(provider));

        SyncRequestDTO dto = SyncRequestDTO.builder()
                .syncType("PRODUCTS")
                .direction("IMPORT")
                .build();

        ErpSyncLog result = service.sync(configId, dto, companyId);
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getRecordsTotal()).isEqualTo(10);
        assertThat(result.getRecordsSynced()).isEqualTo(10);
        assertThat(result.getRecordsFailed()).isEqualTo(0);
        assertThat(result.getErrorMessage()).isNull();
        assertThat(result.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("sync → partial success")
    void sync_partialSuccess() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));
        when(erpSyncLogRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(erpConfigRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ErpProvider provider = mock(ErpProvider.class);
        ErpSyncResult syncResult = ErpSyncResult.builder()
                .success(false)
                .recordsTotal(10)
                .recordsSynced(8)
                .recordsFailed(2)
                .errorMessage("Some errors")
                .build();
        when(provider.importOrders(config)).thenReturn(syncResult);
        when(providerRegistry.getProvider("ODOO")).thenReturn(Optional.of(provider));

        SyncRequestDTO dto = SyncRequestDTO.builder()
                .syncType("ORDERS")
                .direction("IMPORT")
                .build();

        ErpSyncLog result = service.sync(configId, dto, companyId);
        assertThat(result.getStatus()).isEqualTo("PARTIAL");
        assertThat(config.getLastError()).isEqualTo("Some errors");
    }

    @Test
    @DisplayName("sync → failed when all records fail")
    void sync_failed() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));
        when(erpSyncLogRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(erpConfigRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ErpProvider provider = mock(ErpProvider.class);
        ErpSyncResult syncResult = ErpSyncResult.builder()
                .success(false)
                .recordsTotal(5)
                .recordsSynced(0)
                .recordsFailed(5)
                .errorMessage("All failed")
                .build();
        when(provider.importContacts(config)).thenReturn(syncResult);
        when(providerRegistry.getProvider("ODOO")).thenReturn(Optional.of(provider));

        SyncRequestDTO dto = SyncRequestDTO.builder()
                .syncType("CONTACTS")
                .direction("IMPORT")
                .build();

        ErpSyncLog result = service.sync(configId, dto, companyId);
        assertThat(result.getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("sync → exception during sync sets FAILED status")
    void sync_exception() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));
        when(erpSyncLogRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(erpConfigRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ErpProvider provider = mock(ErpProvider.class);
        when(provider.importProducts(config)).thenThrow(new RuntimeException("Sync error"));
        when(providerRegistry.getProvider("ODOO")).thenReturn(Optional.of(provider));

        SyncRequestDTO dto = SyncRequestDTO.builder()
                .syncType("PRODUCTS")
                .direction("IMPORT")
                .build();

        ErpSyncLog result = service.sync(configId, dto, companyId);
        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getErrorMessage()).isEqualTo("Sync error");
        assertThat(config.getSyncStatus()).isEqualTo("ERROR");
        assertThat(config.getLastError()).isEqualTo("Sync error");
    }

    @Test
    @DisplayName("sync → no provider throws IllegalArgumentException")
    void sync_noProvider() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));
        when(providerRegistry.getProvider("ODOO")).thenReturn(Optional.empty());

        SyncRequestDTO dto = SyncRequestDTO.builder()
                .syncType("PRODUCTS")
                .direction("IMPORT")
                .build();

        assertThatThrownBy(() -> service.sync(configId, dto, companyId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Aucun provider ERP trouvé pour le type: ODOO");
    }

    @Test
    @DisplayName("sync → ORDERS EXPORT calls exportOrders")
    void sync_ordersExport() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));
        when(erpSyncLogRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(erpConfigRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(shipmentOrderRepo.findByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(ShipmentOrder.builder().build()));

        ErpProvider provider = mock(ErpProvider.class);
        ErpSyncResult syncResult = ErpSyncResult.builder().success(true).build();
        when(provider.exportOrders(any(), anyList())).thenReturn(syncResult);
        when(providerRegistry.getProvider("ODOO")).thenReturn(Optional.of(provider));

        SyncRequestDTO dto = SyncRequestDTO.builder()
                .syncType("ORDERS")
                .direction("EXPORT")
                .build();

        ErpSyncLog result = service.sync(configId, dto, companyId);
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        verify(provider).exportOrders(eq(config), anyList());
    }

    @Test
    @DisplayName("sync → INVOICES IMPORT calls importOrders")
    void sync_invoicesImport() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));
        when(erpSyncLogRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(erpConfigRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ErpProvider provider = mock(ErpProvider.class);
        ErpSyncResult syncResult = ErpSyncResult.builder().success(true).build();
        when(provider.importOrders(config)).thenReturn(syncResult);
        when(providerRegistry.getProvider("ODOO")).thenReturn(Optional.of(provider));

        SyncRequestDTO dto = SyncRequestDTO.builder()
                .syncType("INVOICES")
                .direction("IMPORT")
                .build();

        ErpSyncLog result = service.sync(configId, dto, companyId);
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        verify(provider).importOrders(config);
    }

    @Test
    @DisplayName("sync → INVOICES EXPORT calls exportShipments")
    void sync_invoicesExport() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));
        when(erpSyncLogRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(erpConfigRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(shipmentOrderRepo.findByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(ShipmentOrder.builder().build()));

        ErpProvider provider = mock(ErpProvider.class);
        ErpSyncResult syncResult = ErpSyncResult.builder().success(true).build();
        when(provider.exportShipments(any(), anyList())).thenReturn(syncResult);
        when(providerRegistry.getProvider("ODOO")).thenReturn(Optional.of(provider));

        SyncRequestDTO dto = SyncRequestDTO.builder()
                .syncType("INVOICES")
                .direction("EXPORT")
                .build();

        ErpSyncLog result = service.sync(configId, dto, companyId);
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        verify(provider).exportShipments(eq(config), anyList());
    }

    @Test
    @DisplayName("sync → CONTACTS EXPORT returns warning")
    void sync_contactsExport() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));
        when(erpSyncLogRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(erpConfigRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ErpProvider provider = mock(ErpProvider.class);
        when(providerRegistry.getProvider("ODOO")).thenReturn(Optional.of(provider));

        SyncRequestDTO dto = SyncRequestDTO.builder()
                .syncType("CONTACTS")
                .direction("EXPORT")
                .build();

        ErpSyncLog result = service.sync(configId, dto, companyId);
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        assertThat(result.getRecordsTotal()).isEqualTo(0);
    }

    @Test
    @DisplayName("sync → SHIPMENTS EXPORT calls exportShipments")
    void sync_shipmentsExport() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));
        when(erpSyncLogRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(erpConfigRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(shipmentOrderRepo.findByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(ShipmentOrder.builder().build()));

        ErpProvider provider = mock(ErpProvider.class);
        ErpSyncResult syncResult = ErpSyncResult.builder().success(true).build();
        when(provider.exportShipments(any(), anyList())).thenReturn(syncResult);
        when(providerRegistry.getProvider("ODOO")).thenReturn(Optional.of(provider));

        SyncRequestDTO dto = SyncRequestDTO.builder()
                .syncType("SHIPMENTS")
                .direction("EXPORT")
                .build();

        ErpSyncLog result = service.sync(configId, dto, companyId);
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("sync → SHIPMENTS IMPORT calls importOrders")
    void sync_shipmentsImport() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));
        when(erpSyncLogRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(erpConfigRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ErpProvider provider = mock(ErpProvider.class);
        ErpSyncResult syncResult = ErpSyncResult.builder().success(true).build();
        when(provider.importOrders(config)).thenReturn(syncResult);
        when(providerRegistry.getProvider("ODOO")).thenReturn(Optional.of(provider));

        SyncRequestDTO dto = SyncRequestDTO.builder()
                .syncType("SHIPMENTS")
                .direction("IMPORT")
                .build();

        ErpSyncLog result = service.sync(configId, dto, companyId);
        assertThat(result.getStatus()).isEqualTo("SUCCESS");
        verify(provider).importOrders(config);
    }

    @Test
    @DisplayName("sync → unknown sync type returns FAILED")
    void sync_unknownType() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));
        when(erpSyncLogRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(erpConfigRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        ErpProvider provider = mock(ErpProvider.class);
        when(providerRegistry.getProvider("ODOO")).thenReturn(Optional.of(provider));

        SyncRequestDTO dto = SyncRequestDTO.builder()
                .syncType("UNKNOWN")
                .direction("IMPORT")
                .build();

        ErpSyncLog result = service.sync(configId, dto, companyId);
        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getErrorMessage()).contains("Type de synchronisation inconnu");
    }

    @Test
    @DisplayName("sync → not found throws")
    void sync_notFound() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.empty());

        SyncRequestDTO dto = SyncRequestDTO.builder()
                .syncType("PRODUCTS")
                .direction("IMPORT")
                .build();

        assertThatThrownBy(() -> service.sync(configId, dto, companyId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("sync → wrong company throws")
    void sync_wrongCompany() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(UUID.randomUUID(), "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));

        SyncRequestDTO dto = SyncRequestDTO.builder()
                .syncType("PRODUCTS")
                .direction("IMPORT")
                .build();

        assertThatThrownBy(() -> service.sync(configId, dto, companyId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getSyncLogs → returns from repo")
    void getSyncLogs() {
        UUID companyId = UUID.randomUUID();
        var log1 = ErpSyncLog.builder().syncType("PRODUCTS").direction("IMPORT").status("SUCCESS").build();
        var log2 = ErpSyncLog.builder().syncType("ORDERS").direction("EXPORT").status("FAILED").build();
        when(erpSyncLogRepo.findTop5ByCompanyIdOrderByStartedAtDesc(companyId))
                .thenReturn(List.of(log1, log2));

        List<ErpSyncLogDTO> result = service.getSyncLogs(companyId);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getSyncType()).isEqualTo("PRODUCTS");
        assertThat(result.get(1).getStatus()).isEqualTo("FAILED");
    }

    @Test
    @DisplayName("getSyncLogs → handles null erpConfig")
    void getSyncLogs_nullConfig() {
        UUID companyId = UUID.randomUUID();
        var log = ErpSyncLog.builder()
                .erpConfig(null)
                .syncType("PRODUCTS")
                .direction("IMPORT")
                .status("SUCCESS")
                .build();
        when(erpSyncLogRepo.findTop5ByCompanyIdOrderByStartedAtDesc(companyId))
                .thenReturn(List.of(log));

        List<ErpSyncLogDTO> result = service.getSyncLogs(companyId);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getErpConfigId()).isNull();
        assertThat(result.get(0).getErpTypeName()).isNull();
    }

    @Test
    @DisplayName("getHealth → returns health DTOs")
    void getHealth() {
        UUID companyId = UUID.randomUUID();
        var config1 = config(companyId, "ODOO");
        config1.setName("Odoo ERP");
        config1.setSyncStatus("IDLE");
        config1.setLastError(null);
        config1.setLastSyncAt(LocalDateTime.now().minusDays(1));
        var config2 = config(companyId, "SAP");
        config2.setName("SAP ERP");
        config2.setSyncStatus("ERROR");
        config2.setLastError("Connection timeout");
        when(erpConfigRepo.findByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(config1, config2));

        List<ErpHealthDTO> result = service.getHealth(companyId);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getErpType()).isEqualTo("ODOO");
        assertThat(result.get(0).getName()).isEqualTo("Odoo ERP");
        assertThat(result.get(0).getSyncStatus()).isEqualTo("IDLE");
        assertThat(result.get(1).getErpType()).isEqualTo("SAP");
        assertThat(result.get(1).getLastError()).isEqualTo("Connection timeout");
    }

    @Test
    @DisplayName("getProducts → returns products from provider")
    void getProducts_success() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));

        ErpProvider provider = mock(ErpProvider.class);
        when(provider.getProducts(config)).thenReturn(List.of(Map.of("name", "Product A")));
        when(providerRegistry.getProvider("ODOO")).thenReturn(Optional.of(provider));

        List<Map<String, Object>> result = service.getProducts(configId, companyId);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("name")).isEqualTo("Product A");
    }

    @Test
    @DisplayName("getProducts → no provider returns empty")
    void getProducts_noProvider() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));
        when(providerRegistry.getProvider("ODOO")).thenReturn(Optional.empty());

        List<Map<String, Object>> result = service.getProducts(configId, companyId);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getProducts → exception returns empty")
    void getProducts_exception() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));

        ErpProvider provider = mock(ErpProvider.class);
        when(provider.getProducts(config)).thenThrow(new RuntimeException("API error"));
        when(providerRegistry.getProvider("ODOO")).thenReturn(Optional.of(provider));

        List<Map<String, Object>> result = service.getProducts(configId, companyId);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getProducts → not found throws")
    void getProducts_notFound() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProducts(configId, companyId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getOrders → returns orders from provider")
    void getOrders_success() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));

        ErpProvider provider = mock(ErpProvider.class);
        when(provider.getOrders(config)).thenReturn(List.of(Map.of("name", "Order A")));
        when(providerRegistry.getProvider("ODOO")).thenReturn(Optional.of(provider));

        List<Map<String, Object>> result = service.getOrders(configId, companyId);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("name")).isEqualTo("Order A");
    }

    @Test
    @DisplayName("getOrders → exception returns empty")
    void getOrders_exception() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));

        ErpProvider provider = mock(ErpProvider.class);
        when(provider.getOrders(config)).thenThrow(new RuntimeException("API error"));
        when(providerRegistry.getProvider("ODOO")).thenReturn(Optional.of(provider));

        List<Map<String, Object>> result = service.getOrders(configId, companyId);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getContacts → returns contacts from provider")
    void getContacts_success() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));

        ErpProvider provider = mock(ErpProvider.class);
        when(provider.getContacts(config)).thenReturn(List.of(Map.of("name", "Contact A")));
        when(providerRegistry.getProvider("ODOO")).thenReturn(Optional.of(provider));

        List<Map<String, Object>> result = service.getContacts(configId, companyId);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("name")).isEqualTo("Contact A");
    }

    @Test
    @DisplayName("getContacts → exception returns empty")
    void getContacts_exception() {
        UUID companyId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        var config = config(companyId, "ODOO");
        config.setId(configId);
        when(erpConfigRepo.findById(configId)).thenReturn(Optional.of(config));

        ErpProvider provider = mock(ErpProvider.class);
        when(provider.getContacts(config)).thenThrow(new RuntimeException("API error"));
        when(providerRegistry.getProvider("ODOO")).thenReturn(Optional.of(provider));

        List<Map<String, Object>> result = service.getContacts(configId, companyId);
        assertThat(result).isEmpty();
    }
}
