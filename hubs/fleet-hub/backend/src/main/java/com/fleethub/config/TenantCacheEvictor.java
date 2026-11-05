package com.fleethub.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ConcurrentMap;

/**
 * Vide les caches KPI / tableau de bord d'UNE société, après une écriture qui peut
 * changer ses indicateurs. Les clés sont préfixées par la société
 * ({@code <companyId>:<méthode>:<paramètres>}, voir CacheConfig#tenantKeyGenerator) :
 * on ne supprime que les siennes, les autres sociétés gardent leur cache.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantCacheEvictor {

    /** Caches dont la clé commence par l'identifiant de la société. */
    static final List<String> TENANT_CACHES =
            List.of("kpi.couples", "kpi.detail", "kpi.truck", "kpi.northstar", "dashboard.summary");

    private final CacheManager cacheManager;

    public void evictCompany(Long companyId) {
        if (companyId == null) return;
        String prefix = companyId + ":";
        for (String name : TENANT_CACHES) {
            Cache cache = cacheManager.getCache(name);
            if (cache == null) continue;
            try {
                if (cache.getNativeCache() instanceof ConcurrentMap<?, ?> map) {
                    map.keySet().removeIf(key -> key.toString().startsWith(prefix));
                } else {
                    cache.clear(); // autre implémentation : toutes les sociétés, mais jamais obsolète
                }
            } catch (RuntimeException e) {
                // Ne jamais faire échouer l'écriture déjà validée : au pire, l'indicateur
                // se met à jour à l'expiration du cache (CacheConfig#EXPIRY_MS).
                log.warn("Invalidation du cache {} impossible pour la société {} : {}", name, companyId, e.getMessage());
            }
        }
    }
}
