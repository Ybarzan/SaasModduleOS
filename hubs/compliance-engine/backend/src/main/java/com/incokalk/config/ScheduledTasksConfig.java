package com.incokalk.config;

import com.incokalk.model.ShipmentOrder;
import com.incokalk.repository.ShipmentOrderRepository;
import com.incokalk.scheduling.DistributedJobLock;
import com.incokalk.service.ApiKeyService;
import com.incokalk.service.LiveTrackingService;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Slf4j
@Configuration
@EnableScheduling
@EnableAsync
@RequiredArgsConstructor
public class ScheduledTasksConfig {

    private final ApiKeyService apiKeyService;
    private final CacheManager cacheManager;
    private final ShipmentOrderRepository shipmentRepo;
    private final LiveTrackingService trackingService;
    private final DistributedJobLock jobLock;

    @Scheduled(cron = "0 0 0 * * *", zone = "UTC")
    @Transactional
    public void resetDailyQuotas() {
        jobLock.runExclusively("reset-daily-quotas", Duration.ofMinutes(10), () -> {
            log.info("[Scheduler] Reset quotas journaliers");
            apiKeyService.resetDailyQuotas();
        });
    }

    @Scheduled(cron = "0 0 3 * * MON", zone = "UTC")
    public void invalidateCustomsCache() {
        jobLock.runExclusively("invalidate-customs-cache", Duration.ofMinutes(5), () -> {
            var cache = cacheManager.getCache("customs-duties");
            if (cache != null) { cache.clear(); log.info("[Scheduler] Cache customs-duties invalidé"); }
        });
    }

    @Scheduled(fixedRate = 300000)
    @Transactional(readOnly = true)
    public void autoSyncInTransitShipments() {
        jobLock.runExclusively("auto-sync-in-transit-shipments", Duration.ofMinutes(4), () -> {
            try {
                List<ShipmentOrder> inTransit = shipmentRepo.findByStatus(
                    ShipmentOrder.Status.IN_TRANSIT);
                if (inTransit.isEmpty()) return;

                log.debug("[Scheduler] Auto-sync tracking pour {} expéditions en transit", inTransit.size());
                int synced = 0;

                for (ShipmentOrder shipment : inTransit) {
                    try {
                        UUID companyId = shipment.getCompany() != null ? shipment.getCompany().getId() : null;
                        if (companyId == null) continue;

                        TenantContext.set(companyId);
                        trackingService.syncTrackingToEvents(shipment.getId(), companyId);
                        synced++;
                    } catch (Exception e) {
                        log.debug("[Scheduler] Sync échouée pour {}: {}",
                            shipment.getOrderNumber(), e.getMessage());
                    } finally {
                        TenantContext.clear();
                    }
                }

                log.info("[Scheduler] Auto-sync terminé: {}/{} expéditions synchronisées", synced, inTransit.size());
            } catch (Exception e) {
                log.debug("[Scheduler] Erreur auto-sync tracking: {}", e.getMessage());
            }
        });
    }
}
