package com.fleethub.repository;

import com.fleethub.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    void deleteByCompany_Id(Long companyId);
}
