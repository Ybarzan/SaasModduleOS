package com.incokalk.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class LoginRateLimiter {

    // Fenêtre de blocage réelle : 15 minutes (voir createLoginBucket). Évincer
    // une entrée après 2x cette fenêtre sans accès garantit qu'on ne purge
    // jamais un bucket encore potentiellement bloquant. Sans cette purge, un
    // attaquant qui teste des milliers d'emails distincts (plutôt que de
    // réessayer le même) ferait grossir la map indéfiniment -- une fuite
    // mémoire, puisque seul reset() (login réussi) retirait une entrée.
    private static final Duration EVICT_AFTER = Duration.ofMinutes(30);

    private final ConcurrentHashMap<String, TrackedBucket> loginBuckets = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemUTC();

    private Bucket createLoginBucket() {
        Bandwidth limit = Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(15)));
        return Bucket.builder().addLimit(limit).build();
    }

    public boolean isBlocked(String email) {
        TrackedBucket tracked = loginBuckets.computeIfAbsent(
                email.toLowerCase(), k -> new TrackedBucket(createLoginBucket()));
        tracked.lastAccess = clock.instant();
        ConsumptionProbe probe = tracked.bucket.tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long waitMinutes = probe.getNanosToWaitForRefill() / 60_000_000_000L;
            log.warn("[Security] Trop de tentatives de connexion pour {}: bloqué {} min", email, waitMinutes + 1);
            return true;
        }
        return false;
    }

    public void reset(String email) {
        loginBuckets.remove(email.toLowerCase());
    }

    @Scheduled(fixedRate = 15, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
    void evictStaleEntries() {
        Instant cutoff = clock.instant().minus(EVICT_AFTER);
        int before = loginBuckets.size();
        loginBuckets.values().removeIf(tracked -> tracked.lastAccess.isBefore(cutoff));
        int evicted = before - loginBuckets.size();
        if (evicted > 0) {
            log.debug("[Security] LoginRateLimiter : {} entrée(s) expirée(s) purgée(s)", evicted);
        }
    }

    private static final class TrackedBucket {
        final Bucket bucket;
        volatile Instant lastAccess;

        TrackedBucket(Bucket bucket) {
            this.bucket = bucket;
            this.lastAccess = Instant.now();
        }
    }
}
