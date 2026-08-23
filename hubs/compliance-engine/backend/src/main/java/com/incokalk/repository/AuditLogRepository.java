package com.incokalk.repository;

import com.incokalk.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {

    Page<AuditLog> findByCompanyIdOrderByCreatedAtDesc(UUID companyId, Pageable pageable);

    Page<AuditLog> findByCompanyIdAndActionOrderByCreatedAtDesc(UUID companyId, String action, Pageable pageable);

    Page<AuditLog> findByCompanyIdAndEntityTypeOrderByCreatedAtDesc(UUID companyId, String entityType, Pageable pageable);

    Page<AuditLog> findByCompanyIdAndUserIdOrderByCreatedAtDesc(UUID companyId, UUID userId, Pageable pageable);

    Page<AuditLog> findByCompanyIdAndCreatedAtBetweenOrderByCreatedAtDesc(
            UUID companyId, LocalDateTime from, LocalDateTime to, Pageable pageable);

    @Query("SELECT a.action, COUNT(a) FROM AuditLog a WHERE a.company.id = :companyId GROUP BY a.action")
    List<Object[]> countByActionGrouped(UUID companyId);

    @Query("SELECT a.entityType, COUNT(a) FROM AuditLog a WHERE a.company.id = :companyId GROUP BY a.entityType")
    List<Object[]> countByEntityGrouped(UUID companyId);

    long countByCompanyId(UUID companyId);
}
