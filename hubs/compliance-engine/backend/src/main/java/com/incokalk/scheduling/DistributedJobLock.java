package com.incokalk.scheduling;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

// Empêche deux instances backend d'exécuter le même @Scheduled en même temps.
// Verrou Redis à bail (SET NX PX) : si aucune instance Redis n'est configurée
// (dev/local/test, une seule instance), le job s'exécute simplement toujours --
// c'est un no-op transparent, pas une dépendance dure à Redis.
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedJobLock {

    private static final String KEY_PREFIX = "job-lock:";

    private final Optional<StringRedisTemplate> redis;

    public void runExclusively(String jobName, Duration leaseDuration, Runnable job) {
        if (redis.isEmpty()) {
            job.run();
            return;
        }
        String key = KEY_PREFIX + jobName;
        Boolean acquired = redis.get().opsForValue()
            .setIfAbsent(key, Instant.now().toString(), leaseDuration);
        if (!Boolean.TRUE.equals(acquired)) {
            log.debug("[JobLock] {} déjà pris par une autre instance, ignoré", jobName);
            return;
        }
        try {
            job.run();
        } finally {
            redis.get().delete(key);
        }
    }
}
