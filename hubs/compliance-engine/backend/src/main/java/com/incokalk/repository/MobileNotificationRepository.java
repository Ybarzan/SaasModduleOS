package com.incokalk.repository;

import com.incokalk.model.MobileNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface MobileNotificationRepository extends JpaRepository<MobileNotification, UUID> {

    List<MobileNotification> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<MobileNotification> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    List<MobileNotification> findByUserIdAndIsReadFalseOrderByCreatedAtDesc(UUID userId);

    int countByUserIdAndIsReadFalse(UUID userId);

    @Modifying
    @Query("UPDATE MobileNotification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP WHERE n.id = :id AND n.userId = :userId")
    int markAsRead(@Param("id") UUID id, @Param("userId") UUID userId);

    @Modifying
    @Query("UPDATE MobileNotification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP WHERE n.userId = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") UUID userId);
}
