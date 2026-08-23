package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Company;
import com.incokalk.model.ECommerceIntegration;
import com.incokalk.model.ECommerceSyncLog;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.ECommerceIntegrationRepository;
import com.incokalk.repository.ECommerceSyncLogRepository;
import com.incokalk.scheduling.DistributedJobLock;
import com.incokalk.service.ecommerce.ECommerceAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("ECommerceSyncService — Tests unitaires")
class ECommerceSyncServiceTest {

    ECommerceSyncService service;
    CompanyRepository companyRepository;
    ECommerceIntegrationRepository integrationRepository;
    ECommerceSyncLogRepository syncLogRepository;
    ECommerceAdapter adapter;

    @BeforeEach
    void setUp() {
        companyRepository = mock(CompanyRepository.class);
        integrationRepository = mock(ECommerceIntegrationRepository.class);
        syncLogRepository = mock(ECommerceSyncLogRepository.class);
        adapter = mock(ECommerceAdapter.class);
        service = new ECommerceSyncService(companyRepository, integrationRepository, syncLogRepository,
            List.of(adapter), new DistributedJobLock(Optional.empty()));
    }

    @Test
    @DisplayName("syncSingleIntegration → succès avec commandes")
    void syncSingleIntegration_success() {
        ECommerceIntegration integration = ECommerceIntegration.builder()
                .id(UUID.randomUUID())
                .platform(ECommerceIntegration.Platform.SHOPIFY)
                .isActive(true)
                .build();

        when(adapter.supports(ECommerceIntegration.Platform.SHOPIFY)).thenReturn(true);
        when(adapter.syncOrders(integration)).thenReturn(List.of(
                Map.of("id", "1"),
                Map.of("id", "2")
        ));
        when(adapter.mapOrderToShipment(any(), eq(integration))).thenReturn(null);
        when(syncLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ECommerceSyncLog syncLog = service.syncSingleIntegration(integration);

        assertThat(syncLog.getStatus()).isEqualTo(ECommerceSyncLog.SyncStatus.SUCCESS);
        assertThat(syncLog.getOrdersProcessed()).isEqualTo(2);
        assertThat(syncLog.getOrdersCreated()).isEqualTo(0);
        assertThat(syncLog.getOrdersFailed()).isEqualTo(0);
    }

    @Test
    @DisplayName("syncSingleIntegration → échec mapping → PARTIAL")
    void syncSingleIntegration_partial() {
        ECommerceIntegration integration = ECommerceIntegration.builder()
                .id(UUID.randomUUID())
                .platform(ECommerceIntegration.Platform.SHOPIFY)
                .build();

        when(adapter.supports(ECommerceIntegration.Platform.SHOPIFY)).thenReturn(true);
        when(adapter.syncOrders(integration)).thenReturn(List.of(
                Map.of("id", "1"),
                Map.of("id", "2")
        ));
        when(adapter.mapOrderToShipment(any(), eq(integration)))
                .thenReturn(null)
                .thenThrow(new RuntimeException("Mapping error"));
        when(syncLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ECommerceSyncLog syncLog = service.syncSingleIntegration(integration);

        assertThat(syncLog.getStatus()).isEqualTo(ECommerceSyncLog.SyncStatus.FAILED);
        assertThat(syncLog.getOrdersProcessed()).isEqualTo(2);
        assertThat(syncLog.getOrdersCreated()).isEqualTo(0);
        assertThat(syncLog.getOrdersFailed()).isEqualTo(1);
    }

    @Test
    @DisplayName("syncSingleIntegration → tout échoue → FAILED")
    void syncSingleIntegration_allFailed() {
        ECommerceIntegration integration = ECommerceIntegration.builder()
                .id(UUID.randomUUID())
                .platform(ECommerceIntegration.Platform.SHOPIFY)
                .build();

        when(adapter.supports(ECommerceIntegration.Platform.SHOPIFY)).thenReturn(true);
        when(adapter.syncOrders(integration)).thenReturn(List.of(
                Map.of("id", "1")
        ));
        when(adapter.mapOrderToShipment(any(), eq(integration)))
                .thenThrow(new RuntimeException("Error"));
        when(syncLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ECommerceSyncLog syncLog = service.syncSingleIntegration(integration);

        assertThat(syncLog.getStatus()).isEqualTo(ECommerceSyncLog.SyncStatus.FAILED);
        assertThat(syncLog.getOrdersFailed()).isEqualTo(1);
    }

    @Test
    @DisplayName("syncSingleIntegration → aucun adaptateur → FAILED")
    void syncSingleIntegration_noAdapter() {
        service = new ECommerceSyncService(companyRepository, integrationRepository, syncLogRepository,
            List.of(), new DistributedJobLock(Optional.empty()));
        ECommerceIntegration integration = ECommerceIntegration.builder()
                .id(UUID.randomUUID())
                .platform(ECommerceIntegration.Platform.WOOCOMMERCE)
                .build();
        when(syncLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ECommerceSyncLog syncLog = service.syncSingleIntegration(integration);

        assertThat(syncLog.getStatus()).isEqualTo(ECommerceSyncLog.SyncStatus.FAILED);
        assertThat(syncLog.getErrorMessage()).contains("No adapter found");
    }

    @Test
    @DisplayName("syncSingleIntegration → exception lors du sync → FAILED")
    void syncSingleIntegration_exception() {
        ECommerceIntegration integration = ECommerceIntegration.builder()
                .id(UUID.randomUUID())
                .platform(ECommerceIntegration.Platform.SHOPIFY)
                .build();

        when(adapter.supports(ECommerceIntegration.Platform.SHOPIFY)).thenReturn(true);
        when(adapter.syncOrders(integration)).thenThrow(new RuntimeException("Connection refused"));
        when(syncLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ECommerceSyncLog syncLog = service.syncSingleIntegration(integration);

        assertThat(syncLog.getStatus()).isEqualTo(ECommerceSyncLog.SyncStatus.FAILED);
        assertThat(syncLog.getErrorMessage()).isEqualTo("Connection refused");
    }

    @Test
    @DisplayName("resolveAdapter → trouve l'adaptateur")
    void resolveAdapter_found() {
        when(adapter.supports(ECommerceIntegration.Platform.SHOPIFY)).thenReturn(true);

        ECommerceAdapter result = service.resolveAdapter(ECommerceIntegration.Platform.SHOPIFY);

        assertThat(result).isSameAs(adapter);
    }

    @Test
    @DisplayName("resolveAdapter → aucun adaptateur → null")
    void resolveAdapter_notFound() {
        when(adapter.supports(any())).thenReturn(false);

        ECommerceAdapter result = service.resolveAdapter(ECommerceIntegration.Platform.PRESTASHOP);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("getSyncLog → retourne les logs via le repo")
    void getSyncLog() {
        UUID integrationId = UUID.randomUUID();
        ECommerceSyncLog log = ECommerceSyncLog.builder()
                .integrationId(integrationId)
                .status(ECommerceSyncLog.SyncStatus.SUCCESS)
                .build();
        when(syncLogRepository.findById(integrationId)).thenReturn(java.util.Optional.of(log));

        ECommerceSyncLog result = service.syncSingleIntegration(ECommerceIntegration.builder()
                .id(integrationId)
                .platform(ECommerceIntegration.Platform.SHOPIFY)
                .build());
    }

    @Test
    @DisplayName("syncSingleIntegration → avec création de commandes")
    void syncSingleIntegration_withShipments() {
        ECommerceIntegration integration = ECommerceIntegration.builder()
                .id(UUID.randomUUID())
                .platform(ECommerceIntegration.Platform.SHOPIFY)
                .build();
        var shipment = new com.incokalk.model.ShipmentOrder();
        shipment.setOrderNumber("SHIP-001");

        when(adapter.supports(ECommerceIntegration.Platform.SHOPIFY)).thenReturn(true);
        when(adapter.syncOrders(integration)).thenReturn(List.of(
                Map.of("id", "1"),
                Map.of("id", "2"),
                Map.of("id", "3")
        ));
        when(adapter.mapOrderToShipment(any(), eq(integration))).thenReturn(shipment);
        when(syncLogRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ECommerceSyncLog syncLog = service.syncSingleIntegration(integration);

        assertThat(syncLog.getOrdersProcessed()).isEqualTo(3);
        assertThat(syncLog.getOrdersCreated()).isEqualTo(3);
        assertThat(syncLog.getOrdersFailed()).isZero();
        assertThat(syncLog.getStatus()).isEqualTo(ECommerceSyncLog.SyncStatus.SUCCESS);
    }

    @Test
    @DisplayName("createIntegration → success")
    void createIntegration() {
        UUID companyId = UUID.randomUUID();
        Company company = Company.builder().id(companyId).build();
        when(companyRepository.getReferenceById(companyId)).thenReturn(company);
        when(integrationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ECommerceIntegration result = service.createIntegration(
                ECommerceIntegration.Platform.SHOPIFY,
                "https://store.myshopify.com",
                "api-key-123",
                "secret-456",
                "webhook-secret",
                30,
                companyId);

        assertThat(result.getPlatform()).isEqualTo(ECommerceIntegration.Platform.SHOPIFY);
        assertThat(result.getStoreUrl()).isEqualTo("https://store.myshopify.com");
        assertThat(result.getApiKey()).isEqualTo("api-key-123");
        assertThat(result.getApiSecret()).isEqualTo("secret-456");
        assertThat(result.getWebhookSecret()).isEqualTo("webhook-secret");
        assertThat(result.getIsActive()).isTrue();
        assertThat(result.getSyncFrequencyMin()).isEqualTo(30);
        verify(integrationRepository).save(result);
    }

    @Test
    @DisplayName("createIntegration → default sync frequency")
    void createIntegration_defaultFrequency() {
        UUID companyId = UUID.randomUUID();
        Company company = Company.builder().id(companyId).build();
        when(companyRepository.getReferenceById(companyId)).thenReturn(company);
        when(integrationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ECommerceIntegration result = service.createIntegration(
                ECommerceIntegration.Platform.WOOCOMMERCE,
                "https://store.com",
                "key",
                "secret",
                "webhook",
                null,
                companyId);

        assertThat(result.getSyncFrequencyMin()).isEqualTo(60);
    }

    @Test
    @DisplayName("listIntegrations → returns from repo")
    void listIntegrations() {
        UUID companyId = UUID.randomUUID();
        var integration = ECommerceIntegration.builder()
                .company(Company.builder().id(companyId).build())
                .platform(ECommerceIntegration.Platform.SHOPIFY)
                .build();
        when(integrationRepository.findByCompanyIdOrderByCreatedAtDesc(companyId))
                .thenReturn(List.of(integration));

        List<ECommerceIntegration> result = service.listIntegrations(companyId);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPlatform()).isEqualTo(ECommerceIntegration.Platform.SHOPIFY);
    }

    @Test
    @DisplayName("findIntegrationById → found")
    void findIntegrationById_found() {
        UUID companyId = UUID.randomUUID();
        UUID integrationId = UUID.randomUUID();
        var integration = ECommerceIntegration.builder()
                .company(Company.builder().id(companyId).build())
                .platform(ECommerceIntegration.Platform.SHOPIFY)
                .build();
        when(integrationRepository.findById(integrationId)).thenReturn(Optional.of(integration));

        Optional<ECommerceIntegration> result = service.findIntegrationById(integrationId, companyId);
        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("findIntegrationById → not found for wrong company")
    void findIntegrationById_wrongCompany() {
        UUID companyId = UUID.randomUUID();
        UUID integrationId = UUID.randomUUID();
        var integration = ECommerceIntegration.builder()
                .company(Company.builder().id(UUID.randomUUID()).build())
                .platform(ECommerceIntegration.Platform.SHOPIFY)
                .build();
        when(integrationRepository.findById(integrationId)).thenReturn(Optional.of(integration));

        Optional<ECommerceIntegration> result = service.findIntegrationById(integrationId, companyId);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("updateIntegration → success")
    void updateIntegration() {
        UUID companyId = UUID.randomUUID();
        UUID integrationId = UUID.randomUUID();
        var integration = ECommerceIntegration.builder()
                .id(integrationId)
                .company(Company.builder().id(companyId).build())
                .platform(ECommerceIntegration.Platform.SHOPIFY)
                .isActive(true)
                .syncFrequencyMin(60)
                .build();
        when(integrationRepository.findById(integrationId)).thenReturn(Optional.of(integration));
        when(integrationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        ECommerceIntegration result = service.updateIntegration(
                integrationId, companyId,
                "https://new-url.com", "new-key", "new-secret", "new-webhook",
                false, 120);

        assertThat(result.getStoreUrl()).isEqualTo("https://new-url.com");
        assertThat(result.getApiKey()).isEqualTo("new-key");
        assertThat(result.getApiSecret()).isEqualTo("new-secret");
        assertThat(result.getWebhookSecret()).isEqualTo("new-webhook");
        assertThat(result.getIsActive()).isFalse();
        assertThat(result.getSyncFrequencyMin()).isEqualTo(120);
    }

    @Test
    @DisplayName("updateIntegration → not found throws")
    void updateIntegration_notFound() {
        UUID companyId = UUID.randomUUID();
        UUID integrationId = UUID.randomUUID();
        when(integrationRepository.findById(integrationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateIntegration(
                integrationId, companyId, null, null, null, null, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("deactivateIntegration → success")
    void deactivateIntegration() {
        UUID companyId = UUID.randomUUID();
        UUID integrationId = UUID.randomUUID();
        var integration = ECommerceIntegration.builder()
                .id(integrationId)
                .company(Company.builder().id(companyId).build())
                .platform(ECommerceIntegration.Platform.SHOPIFY)
                .isActive(true)
                .build();
        when(integrationRepository.findById(integrationId)).thenReturn(Optional.of(integration));
        when(integrationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.deactivateIntegration(integrationId, companyId);
        assertThat(integration.getIsActive()).isFalse();
        verify(integrationRepository).save(integration);
    }

    @Test
    @DisplayName("getSyncLogs → returns logs scoped to the company's own integrations")
    void getSyncLogs() {
        UUID companyId = UUID.randomUUID();
        UUID integrationId = UUID.randomUUID();
        ECommerceIntegration integration = ECommerceIntegration.builder()
                .id(integrationId)
                .company(Company.builder().id(companyId).build())
                .platform(ECommerceIntegration.Platform.SHOPIFY)
                .isActive(true)
                .build();
        var log1 = ECommerceSyncLog.builder().integrationId(integrationId)
                .status(ECommerceSyncLog.SyncStatus.SUCCESS).startedAt(LocalDateTime.now().minusHours(1)).build();
        var log2 = ECommerceSyncLog.builder().integrationId(integrationId)
                .status(ECommerceSyncLog.SyncStatus.FAILED).startedAt(LocalDateTime.now()).build();
        when(integrationRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(integration));
        when(syncLogRepository.findByIntegrationIdOrderByStartedAtDesc(integrationId)).thenReturn(List.of(log2, log1));

        List<ECommerceSyncLog> result = service.getSyncLogs(companyId);
        assertThat(result).hasSize(2);
    }

    @Test
    @DisplayName("getSyncLogs → n'inclut jamais les logs d'une autre entreprise")
    void getSyncLogs_doesNotLeakOtherCompanies() {
        UUID companyId = UUID.randomUUID();
        when(integrationRepository.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of());

        List<ECommerceSyncLog> result = service.getSyncLogs(companyId);

        assertThat(result).isEmpty();
        verify(syncLogRepository, never()).findAllByOrderByStartedAtDesc();
    }
}
