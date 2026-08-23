package com.incokalk.config;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {

    private static RedisCacheConfiguration ttl(long hours) {
        return RedisCacheConfiguration.defaultCacheConfig().entryTtl(Duration.ofHours(hours));
    }

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return builder -> builder
            .withCacheConfiguration("taric-rates", ttl(24))
            .withCacheConfiguration("taric-hs-descriptions", ttl(24))
            .withCacheConfiguration("vies-check", ttl(24))
            .withCacheConfiguration("eori-check", ttl(24));
    }
}
