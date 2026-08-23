package com.incokalk.repository;

import com.incokalk.model.ErpSyncLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ErpSyncLogRepository extends JpaRepository<ErpSyncLog, UUID> {

    List<ErpSyncLog> findByErpConfigIdOrderByStartedAtDesc(UUID erpConfigId);

    List<ErpSyncLog> findByCompanyIdOrderByStartedAtDesc(UUID companyId);

    List<ErpSyncLog> findTop5ByCompanyIdOrderByStartedAtDesc(UUID companyId);
}
