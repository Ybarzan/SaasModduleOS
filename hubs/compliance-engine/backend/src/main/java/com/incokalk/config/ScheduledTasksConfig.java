package com.incokalk.config;

import com.incokalk.scheduling.DistributedJobLock;
import com.incokalk.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Slf4j
@Configuration
@EnableScheduling
@EnableAsync
@RequiredArgsConstructor
public class ScheduledTasksConfig {

    private final ApiKeyService apiKeyService;
    private final CacheManager cacheManager;
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

    // autoSyncInTransitShipments() a ete retiree avec le domaine Transport :
    // elle lisait ShipmentOrderRepository et appelait LiveTrackingService, tous
    // deux retires vers fleet-hub.
}
