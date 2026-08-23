package com.incokalk.repository;

import com.incokalk.model.ApiKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    List<ApiKey> findByKeyPrefix(String prefix);

    List<ApiKey> findByUserIdAndActiveTrue(UUID userId);

    List<ApiKey> findByCompanyIdAndActiveTrue(UUID companyId);

    @Modifying
    @Query("UPDATE ApiKey k SET k.callsToday = k.callsToday + 1, k.totalCalls = k.totalCalls + 1, k.lastUsed = CURRENT_TIMESTAMP WHERE k.id = :id")
    void incrementCalls(UUID id);

    @Modifying
    @Query("UPDATE ApiKey k SET k.callsToday = 0")
    void resetAllDailyQuotas();

    @Modifying
    @Query("UPDATE ApiKey k SET k.active = false WHERE k.id = :id AND k.company.id = :companyId")
    int revokeKey(UUID id, UUID companyId);
}
