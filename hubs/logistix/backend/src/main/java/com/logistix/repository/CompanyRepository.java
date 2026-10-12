package com.logistix.repository;

import com.logistix.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID> {

    Optional<Company> findByPraxioIntegrationApiKeyAndPraxioIntegrationEnabledTrue(String praxioIntegrationApiKey);
}
