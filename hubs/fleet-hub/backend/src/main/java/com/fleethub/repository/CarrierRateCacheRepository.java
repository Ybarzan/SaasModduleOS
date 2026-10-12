package com.fleethub.repository;

import com.fleethub.model.CarrierRateCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface CarrierRateCacheRepository extends JpaRepository<CarrierRateCache, Long> {

    Optional<CarrierRateCache> findByCacheKeyAndExpiresAtAfter(String cacheKey, LocalDateTime now);

    @Modifying
    @Query("DELETE FROM CarrierRateCache c WHERE c.expiresAt <= :now")
    int deleteExpired(LocalDateTime now);
}
