package com.incokalk.config;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

// Backend du rate limiting distribué (Bucket4j + Redis) : seuls les seaux de quota sont
// partagés entre instances. N'existe qu'en profil prod, là où spring.data.redis.host est
// défini ; en dev/local/test, RateLimitFilter retombe sur son ConcurrentHashMap en mémoire.
@Configuration
@ConditionalOnProperty(prefix = "spring.data.redis", name = "host")
public class RateLimitRedisConfig {

    @Value("${spring.data.redis.host}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Bean(destroyMethod = "shutdown")
    public RedisClient bucket4jRedisClient() {
        // Sans .withTimeout() explicite, Lettuce retombe sur son defaut de 60s -- bloquer
        // 60s sur le chemin chaud de CHAQUE requete /v1/** si Redis est injoignable est pire
        // que l'incident lui-meme. 2s est deja tres genereux pour un aller-retour local ;
        // RateLimitFilter.doFilterInternal reste responsable du fail-open si ce timeout
        // (ou toute autre erreur Redis) se declenche quand meme.
        RedisURI.Builder uriBuilder = RedisURI.builder()
            .withHost(host)
            .withPort(port)
            .withTimeout(Duration.ofSeconds(2));
        if (password != null && !password.isBlank()) {
            uriBuilder.withPassword(password.toCharArray());
        }
        return RedisClient.create(uriBuilder.build());
    }

    @Bean(destroyMethod = "close")
    public StatefulRedisConnection<byte[], byte[]> bucket4jRedisConnection(RedisClient redisClient) {
        return redisClient.connect(ByteArrayCodec.INSTANCE);
    }

    @Bean
    public ProxyManager<byte[]> bucket4jProxyManager(StatefulRedisConnection<byte[], byte[]> connection) {
        return LettuceBasedProxyManager.builderFor(connection)
            .withExpirationStrategy(
                ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofDays(2)))
            .build();
    }
}
