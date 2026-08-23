package com.incokalk.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class LoginRateLimiter {

    private final ConcurrentHashMap<String, Bucket> loginBuckets = new ConcurrentHashMap<>();

    private Bucket createLoginBucket() {
        Bandwidth limit = Bandwidth.classic(5, Refill.greedy(5, Duration.ofMinutes(15)));
        return Bucket.builder().addLimit(limit).build();
    }

    public boolean isBlocked(String email) {
        Bucket bucket = loginBuckets.computeIfAbsent(email.toLowerCase(), k -> createLoginBucket());
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
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
}
