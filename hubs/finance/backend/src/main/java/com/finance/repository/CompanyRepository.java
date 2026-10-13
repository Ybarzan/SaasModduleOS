package com.finance.repository;

import com.finance.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID> {

    Optional<Company> findByInternalIntegrationApiKeyAndInternalIntegrationEnabledTrue(String internalIntegrationApiKey);
}
