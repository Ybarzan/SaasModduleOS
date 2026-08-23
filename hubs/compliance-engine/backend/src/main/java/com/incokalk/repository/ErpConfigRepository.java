package com.incokalk.repository;

import com.incokalk.model.ErpConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ErpConfigRepository extends JpaRepository<ErpConfig, UUID> {

    List<ErpConfig> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    List<ErpConfig> findByCompanyIdAndErpType(UUID companyId, String erpType);

    List<ErpConfig> findByCompanyIdAndIsActiveTrue(UUID companyId);
}
