package com.incokalk.repository;

import com.incokalk.model.NotificationRule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRuleRepository extends JpaRepository<NotificationRule, UUID> {

    List<NotificationRule> findByCompanyId(UUID companyId);

    Page<NotificationRule> findByCompanyId(UUID companyId, Pageable pageable);

    List<NotificationRule> findByCompanyIdAndEventType(UUID companyId, String eventType);

    List<NotificationRule> findByCompanyIdAndIsActiveTrue(UUID companyId);
}
