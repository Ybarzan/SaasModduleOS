package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Company;
import com.incokalk.model.ECommerceIntegration;
import com.incokalk.model.ECommerceSyncLog;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.ECommerceIntegrationRepository;
import com.incokalk.repository.ECommerceSyncLogRepository;
import com.incokalk.scheduling.DistributedJobLock;
import com.incokalk.service.ecommerce.ECommerceAdapter;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ECommerceSyncService {

    private final CompanyRepository companyRepository;
    private final ECommerceIntegrationRepository integrationRepository;
    private final ECommerceSyncLogRepository syncLogRepository;
    private final List<ECommerceAdapter> adapters;
    private final DistributedJobLock jobLock;

    @PostConstruct
    public void init() {
        log.info("[ECommerceSync] {} e-commerce adapters loaded", adapters.size());
    }

    @Scheduled(fixedDelayString = "${incokalk.ecommerce.sync-interval:300000}")
    @Async("taskExecutor")
    public void scheduledSyncAll() {
        jobLock.runExclusively("ecommerce-sync-all", Duration.ofMinutes(4), () -> {
            log.info("[ECommerceSync] Starting scheduled sync of all active integrations");
            List<ECommerceIntegration> activeIntegrations = new ArrayList<>();
            integrationRepository.findAll().forEach(activeIntegrations::add);
            activeIntegrations = activeIntegrations.stream()
                .filter(i -> Boolean.TRUE.equals(i.getIsActive()))
                .toList();

            for (ECommerceIntegration integration : activeIntegrations) {
                syncSingleIntegration(integration);
            }
        });
    }

    @Transactional
    public ECommerceSyncLog syncSingleIntegration(ECommerceIntegration integration) {
        ECommerceSyncLog syncLog = ECommerceSyncLog.builder()
            .integrationId(integration.getId())
            .status(ECommerceSyncLog.SyncStatus.SUCCESS)
            .ordersProcessed(0)
            .ordersCreated(0)
            .ordersFailed(0)
            .startedAt(LocalDateTime.now())
            .build();

        ECommerceAdapter adapter = resolveAdapter(integration.getPlatform());
        if (adapter == null) {
            syncLog.setStatus(ECommerceSyncLog.SyncStatus.FAILED);
            syncLog.setErrorMessage("No adapter found for platform: " + integration.getPlatform());
            syncLog.setCompletedAt(LocalDateTime.now());
            return syncLogRepository.save(syncLog);
        }

        try {
            List<Map<String, Object>> orders = adapter.syncOrders(integration);
            int processed = 0;
            int created = 0;
            int failed = 0;

            for (Map<String, Object> order : orders) {
                processed++;
                try {
                    ShipmentOrder shipment = adapter.mapOrderToShipment(order, integration);
                    if (shipment != null) {
                        created++;
                    }
                } catch (Exception e) {
                    failed++;
                    log.warn("[ECommerceSync] Failed to map order for integration {}: {}",
                        integration.getId(), e.getMessage());
                }
            }

            syncLog.setOrdersProcessed(processed);
            syncLog.setOrdersCreated(created);
            syncLog.setOrdersFailed(failed);

            if (failed > 0 && created == 0) {
                syncLog.setStatus(ECommerceSyncLog.SyncStatus.FAILED);
            } else if (failed > 0) {
                syncLog.setStatus(ECommerceSyncLog.SyncStatus.PARTIAL);
            }

            integration.setLastSyncAt(LocalDateTime.now());
            integrationRepository.save(integration);

            log.info("[ECommerceSync] Integration {}: processed={}, created={}, failed={}",
                integration.getId(), processed, created, failed);
        } catch (Exception e) {
            syncLog.setStatus(ECommerceSyncLog.SyncStatus.FAILED);
            syncLog.setErrorMessage(e.getMessage());
            log.error("[ECommerceSync] Sync failed for integration {}: {}", integration.getId(), e.getMessage());
        }

        syncLog.setCompletedAt(LocalDateTime.now());
        return syncLogRepository.save(syncLog);
    }

    @Transactional
    public ECommerceIntegration createIntegration(ECommerceIntegration.Platform platform, String storeUrl,
                                                   String apiKey, String apiSecret, String webhookSecret,
                                                   Integer syncFrequencyMin, UUID companyId) {
        Company company = companyRepository.getReferenceById(companyId);
        ECommerceIntegration integration = ECommerceIntegration.builder()
            .company(company)
            .platform(platform)
            .storeUrl(storeUrl)
            .apiKey(apiKey)
            .apiSecret(apiSecret)
            .webhookSecret(webhookSecret)
            .isActive(true)
            .syncFrequencyMin(syncFrequencyMin != null ? syncFrequencyMin : 60)
            .build();
        return integrationRepository.save(integration);
    }

    public List<ECommerceIntegration> listIntegrations(UUID companyId) {
        return integrationRepository.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    public Optional<ECommerceIntegration> findIntegrationById(UUID id, UUID companyId) {
        return integrationRepository.findById(id)
            .filter(i -> i.getCompany() != null && i.getCompany().getId().equals(companyId));
    }

    @Transactional
    public ECommerceIntegration updateIntegration(UUID id, UUID companyId, String storeUrl,
                                                   String apiKey, String apiSecret, String webhookSecret,
                                                   Boolean isActive, Integer syncFrequencyMin) {
        ECommerceIntegration integration = findIntegrationById(id, companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Integration not found"));
        if (storeUrl != null) integration.setStoreUrl(storeUrl);
        if (apiKey != null) integration.setApiKey(apiKey);
        if (apiSecret != null) integration.setApiSecret(apiSecret);
        if (webhookSecret != null) integration.setWebhookSecret(webhookSecret);
        if (isActive != null) integration.setIsActive(isActive);
        if (syncFrequencyMin != null) integration.setSyncFrequencyMin(syncFrequencyMin);
        return integrationRepository.save(integration);
    }

    @Transactional
    public void deactivateIntegration(UUID id, UUID companyId) {
        ECommerceIntegration integration = findIntegrationById(id, companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Integration not found"));
        integration.setIsActive(false);
        integrationRepository.save(integration);
    }

    public List<ECommerceSyncLog> getSyncLogs(UUID companyId) {
        return listIntegrations(companyId).stream()
            .flatMap(integration -> syncLogRepository.findByIntegrationIdOrderByStartedAtDesc(integration.getId()).stream())
            .sorted(Comparator.comparing(ECommerceSyncLog::getStartedAt).reversed())
            .toList();
    }

    public ECommerceAdapter resolveAdapter(ECommerceIntegration.Platform platform) {
        for (ECommerceAdapter adapter : adapters) {
            if (adapter.supports(platform)) {
                return adapter;
            }
        }
        return null;
    }
}
