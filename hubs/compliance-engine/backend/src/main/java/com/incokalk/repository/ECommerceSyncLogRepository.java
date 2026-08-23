package com.incokalk.repository;

import com.incokalk.model.ECommerceSyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ECommerceSyncLogRepository extends JpaRepository<ECommerceSyncLog, UUID> {

    List<ECommerceSyncLog> findByIntegrationIdOrderByStartedAtDesc(UUID integrationId);

    List<ECommerceSyncLog> findAllByOrderByStartedAtDesc();
}
