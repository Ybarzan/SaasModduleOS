package com.incokalk.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.time.Duration;

@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static RedisCacheConfiguration ttl(long hours) {
        return RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofHours(hours));
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return builder -> builder
            .withCacheConfiguration("taric-rates", ttl(24))
            .withCacheConfiguration("taric-hs-descriptions", ttl(24))
            .withCacheConfiguration("vies-check", ttl(24))
            .withCacheConfiguration("eori-check", ttl(24))
            // Sans TTL, un taux faux restait servi indéfiniment. Noms suffixés -v2 : les entrées
            // de l'ancien format de DutyResult (et les taux inventés qu'elles contiennent) sont ignorées.
            .withCacheConfiguration("customs-duties-detailed-v2", ttl(24))
            .withCacheConfiguration("customs-rate-v2", ttl(24));
    }

    /**
     * Un cache est une optimisation : une erreur Redis (valeur non sérialisable, ancien format de
     * classe, Redis indisponible) doit se comporter comme un défaut de cache, pas faire échouer la
     * requête. Avant, tout EORI au format valide renvoyait 500 à cause d'une telle erreur.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("[Cache] Lecture {} impossible (traitée comme absente) : {}", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("[Cache] Écriture {} impossible (ignorée) : {}", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("[Cache] Éviction {} impossible : {}", cache.getName(), e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("[Cache] Purge {} impossible : {}", cache.getName(), e.getMessage());
            }
        };
    }
}
