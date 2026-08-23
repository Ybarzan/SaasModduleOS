package com.incokalk.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.Refill;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// Quotas journaliers par plan (Bucket4j). Backend distribué (Redis) si disponible
// (profil prod, cf. RateLimitRedisConfig) ; sinon repli sur une map en mémoire locale
// à l'instance (dev/local/test, où Redis n'est pas configuré).
@Slf4j
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ConcurrentHashMap<String, Bucket> localBuckets = new ConcurrentHashMap<>();
    private final Optional<ProxyManager<byte[]>> proxyManager;

    @Autowired
    public RateLimitFilter(Optional<ProxyManager<byte[]>> proxyManager) {
        this.proxyManager = proxyManager;
    }

    @Value("${incokalk.rate-limiting.free.requests-per-day:10}")
    private long freeDaily;

    @Value("${incokalk.rate-limiting.starter.requests-per-day:4000}")
    private long starterDaily;

    @Value("${incokalk.rate-limiting.pro.requests-per-day:500}")
    private long proDaily;

    @Value("${incokalk.rate-limiting.api-starter.requests-per-day:2000}")
    private long apiStarterDaily;

    @Value("${incokalk.rate-limiting.api-pro.requests-per-day:10000}")
    private long apiProDaily;

    private static Bandwidth limitFor(long dailyQuota) {
        return Bandwidth.classic(dailyQuota, Refill.greedy(dailyQuota, Duration.ofDays(1)));
    }

    private Bucket resolveBucket(String key, long quota) {
        if (proxyManager.isPresent()) {
            BucketConfiguration configuration = BucketConfiguration.builder().addLimit(limitFor(quota)).build();
            return proxyManager.get().builder()
                .build(key.getBytes(StandardCharsets.UTF_8), () -> configuration);
        }
        return localBuckets.computeIfAbsent(key, k -> Bucket.builder().addLimit(limitFor(quota)).build());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res,
                                     FilterChain chain) throws ServletException, IOException {
        String plan = (String) req.getAttribute("plan");
        long quota = dailyQuotaFor(plan);
        String clientKey = resolveClientKey(req, plan);
        Bucket bucket = resolveBucket(clientKey, quota);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        res.setHeader("X-RateLimit-Limit", String.valueOf(quota));
        res.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));

        if (probe.isConsumed()) {
            chain.doFilter(req, res);
        } else {
            long waitSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000;
            res.setHeader("Retry-After", String.valueOf(waitSeconds));
            res.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write("{\"code\":\"RATE_LIMITED\",\"message\":\"Trop de requêtes. Réessayez dans " + waitSeconds + " secondes.\"}");
        }
    }

    private long dailyQuotaFor(String plan) {
        if (plan == null) return freeDaily;
        return switch (plan.toUpperCase()) {
            case "STARTER" -> starterDaily;
            case "PRO" -> proDaily;
            case "API_STARTER" -> apiStarterDaily;
            case "API_PRO" -> apiProDaily;
            default -> freeDaily;
        };
    }

    private String resolveClientKey(HttpServletRequest req, String plan) {
        Object userId = req.getAttribute("userId");
        String planKey = plan != null ? plan : "FREE";
        if (userId != null) return "user:" + userId + "|" + planKey;
        String ip = req.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank()) ip = req.getRemoteAddr();
        else ip = ip.split(",")[0].trim();
        return "ip:" + ip + "|" + planKey;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String path = req.getRequestURI();
        String ctx = req.getContextPath();
        if (ctx != null && !ctx.isEmpty()) {
            path = path.substring(ctx.length());
        }
        return path.startsWith("/actuator") || path.startsWith("/v1/auth/login")
            || path.startsWith("/v1/auth/register") || path.startsWith("/v1/webhooks");
    }
}
