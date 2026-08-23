package com.fleethub.repository;

import com.fleethub.model.IntegrationConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IntegrationConfigRepository extends JpaRepository<IntegrationConfig, Long> {

    List<IntegrationConfig> findByCompanyId(Long companyId);

    Optional<IntegrationConfig> findByIdAndCompanyId(Long id, Long companyId);

    Optional<IntegrationConfig> findByWebhookKeyAndEnabledTrue(String webhookKey);

    void deleteByCompany_Id(Long companyId);
}
