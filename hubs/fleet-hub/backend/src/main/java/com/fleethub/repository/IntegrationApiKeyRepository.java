package com.fleethub.repository;

import com.fleethub.model.IntegrationApiKey;
import com.fleethub.model.IntegrationConsumer;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface IntegrationApiKeyRepository extends JpaRepository<IntegrationApiKey, Long> {

    @EntityGraph(attributePaths = "company")
    Optional<IntegrationApiKey> findByKeyHash(String keyHash);

    List<IntegrationApiKey> findByCompanyIdOrderByConsumerAsc(Long companyId);

    Optional<IntegrationApiKey> findByCompanyIdAndConsumer(Long companyId, IntegrationConsumer consumer);

    @Modifying
    @Query("UPDATE IntegrationApiKey k SET k.lastUsedAt = :at WHERE k.id = :id")
    void touch(Long id, LocalDateTime at);

    void deleteByCompany_Id(Long companyId);
}
