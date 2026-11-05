package com.fleethub.config;

import com.fleethub.security.TenantContext;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.lang.reflect.Method;
import java.time.Clock;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Cache applicatif en mémoire (KPI, tableau de bord, taux de change).
 * <p>
 * Un cache Redis était configuré pour la production, mais n'a jamais été actif :
 * sa condition {@code @ConditionalOnBean(RedisConnectionFactory)} était évaluée avant
 * l'auto-configuration Redis, et c'est ce cache mémoire — sans aucune expiration —
 * qui servait. Pour un déploiement sur une seule instance, il suffit, à condition :
 * <ul>
 *   <li>d'être invalidé après chaque écriture de la société concernée
 *       (TenantCacheEvictor / KpiCacheInvalidationFilter) ;</li>
 *   <li>d'expirer régulièrement ({@link #EXPIRY_MS}) : les indicateurs dépendent aussi
 *       de la date du jour (fenêtres jour / semaine / mois), et les taux de change de
 *       leur source.</li>
 * </ul>
 * Passer à plusieurs instances demandera un cache partagé (Redis), avec un
 * sérialiseur qui gère les records Java et les types java.time.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    static final long EXPIRY_MS = 5 * 60 * 1000;

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    /** Caches créés à la demande (KPI, dashboard.summary, exchange-rates…). */
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager();
    }

    @Bean("tenantKeyGenerator")
    public KeyGenerator tenantKeyGenerator() {
        return (Object target, Method method, Object... params) -> {
            Long companyId = TenantContext.companyId();
            String methodName = method.getName();
            String paramKey = Arrays.stream(params)
                    .map(Object::toString)
                    .collect(Collectors.joining(":"));
            return companyId + ":" + methodName + ":" + paramKey;
        };
    }

    /** Expiration globale : borne l'ancienneté des données servies et la mémoire occupée. */
    @Scheduled(fixedRate = EXPIRY_MS, initialDelay = EXPIRY_MS)
    public void expireCaches() {
        CacheManager manager = cacheManager();
        manager.getCacheNames().forEach(name -> {
            var cache = manager.getCache(name);
            if (cache != null) cache.clear();
        });
    }
}
