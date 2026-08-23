package com.fleethub.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limite le nombre de requêtes par adresse IP, par route et par fenêtre de temps.
 * Les limites sont configurables par préfixe d'URI (la plus longue préfixe gagne).
 * Désactivé si {@code app.security.rate-limit.enabled=false}.
 */
@Component
public class LoginRateLimitFilter extends OncePerRequestFilter {

    private static final long CLEANUP_INTERVAL_MS = 60_000;

    private final ConcurrentHashMap<String, Counter> attempts = new ConcurrentHashMap<>();
    private volatile long lastCleanup = System.currentTimeMillis();

    /** Préfixe -> limite max par fenêtre. TreeMap pour matching par préfixe le plus long. */
    private final TreeMap<String, Integer> routeLimits = new TreeMap<>();

    @Value("${app.security.rate-limit.enabled:true}")
    private boolean enabled;

    @Value("${app.security.rate-limit.window-seconds:60}")
    private int windowSeconds;

    @Value("${app.security.rate-limit.default-limit:60}")
    private int defaultLimit;

    @Value("${app.security.rate-limit.auth-limit:5}")
    private int authLimit;

    @jakarta.annotation.PostConstruct
    void init() {
        routeLimits.put("/api/auth/login", authLimit);
        routeLimits.put("/api/auth/register", authLimit);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) return true;
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;
        String uri = request.getRequestURI();
        return !uri.startsWith("/api/") || uri.startsWith("/api/webhooks/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        cleanupIfNeeded();

        String ip = clientIp(request);
        String uri = request.getRequestURI();
        int limit = resolveLimit(uri);
        String key = ip + ":" + resolveRouteKey(uri);

        long now = System.currentTimeMillis();
        Counter counter = attempts.computeIfAbsent(key, k -> new Counter(now));

        boolean blocked;
        synchronized (counter) {
            if (now - counter.windowStart > windowSeconds * 1000L) {
                counter.windowStart = now;
                counter.count = 0;
            }
            counter.count++;
            blocked = counter.count > limit;
        }

        if (blocked) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Retry-After", String.valueOf(windowSeconds));
            response.getWriter().write(
                    "{\"message\":\"Trop de requêtes. Réessayez dans une minute.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private int resolveLimit(String uri) {
        Map.Entry<String, Integer> entry = routeLimits.floorEntry(uri);
        if (entry != null && uri.startsWith(entry.getKey())) {
            return entry.getValue();
        }
        return defaultLimit;
    }

    private String resolveRouteKey(String uri) {
        Map.Entry<String, Integer> entry = routeLimits.floorEntry(uri);
        if (entry != null && uri.startsWith(entry.getKey())) {
            return entry.getKey();
        }
        if (uri.startsWith("/api/auth/")) return "/api/auth/*";
        return "/api/*";
    }

    private void cleanupIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastCleanup < CLEANUP_INTERVAL_MS) return;
        lastCleanup = now;

        long cutoff = now - windowSeconds * 1000L;
        Iterator<Map.Entry<String, Counter>> it = attempts.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Counter> entry = it.next();
            if (entry.getValue().windowStart < cutoff) {
                it.remove();
            }
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class Counter {
        long windowStart;
        int count;

        Counter(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
