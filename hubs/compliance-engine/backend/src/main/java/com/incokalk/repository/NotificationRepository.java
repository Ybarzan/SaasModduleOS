package com.incokalk.repository;

import com.incokalk.model.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Page<Notification> findByCompanyIdOrderByCreatedAtDesc(UUID companyId, Pageable pageable);

    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Notification> findByCompanyIdAndStatus(UUID companyId, String status);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.company.id = :companyId AND n.user.id = :userId AND n.status = 'UNREAD'")
    int countByCompanyIdAndUserIdUnread(@Param("companyId") UUID companyId, @Param("userId") UUID userId);

    List<Notification> findByEntityTypeAndEntityId(String entityType, UUID entityId);

    boolean existsByCompanyIdAndStatus(UUID companyId, String status);
}
