package com.incokalk.repository;

import com.incokalk.model.ProviderRateCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProviderRateCacheRepository extends JpaRepository<ProviderRateCache, UUID> {

    Optional<ProviderRateCache> findByCacheKeyAndExpiresAtAfter(String cacheKey, LocalDateTime now);

    @Modifying
    @Query("DELETE FROM ProviderRateCache c WHERE c.expiresAt <= :now")
    int deleteExpired(LocalDateTime now);
}
