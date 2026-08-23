package com.fleethub.repository;

import com.fleethub.model.NotificationRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NotificationRuleRepository extends JpaRepository<NotificationRule, Long> {

    List<NotificationRule> findByCompanyId(Long companyId);

    Optional<NotificationRule> findByIdAndCompanyId(Long id, Long companyId);

    List<NotificationRule> findByCompanyIdAndEnabledTrue(Long companyId);

    void deleteByCompany_Id(Long companyId);
}
