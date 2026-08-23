package com.fleethub.repository;

import com.fleethub.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<Notification> findByCompanyIdAndUserIdIsNullOrderByCreatedAtDesc(Long companyId);

    Optional<Notification> findByIdAndCompanyId(Long id, Long companyId);

    long countByCompanyIdAndReadFalse(Long companyId);

    long countByCompanyIdAndUserIdAndReadFalse(Long companyId, Long userId);

    boolean existsByCompanyIdAndTypeAndEntityIdAndCreatedAtAfter(
            Long companyId, com.fleethub.model.NotificationRule.AlertType type, Long entityId, LocalDateTime since);

    void deleteByCompany_Id(Long companyId);
}
