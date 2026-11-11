package com.fleethub.repository;

import com.fleethub.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    Optional<Company> findByName(String name);

    Optional<Company> findByAccessCode(String accessCode);

    Optional<Company> findByMarketplaceApiKeyAndMarketplaceOptInTrue(String marketplaceApiKey);

    Optional<Company> findByInternalIntegrationApiKeyAndInternalIntegrationEnabledTrue(String internalIntegrationApiKey);

    Optional<Company> findBySubscriptionId(String subscriptionId);

    List<Company> findAllByOrderByCreatedAtDesc();

    /** Verrou d'écriture sur la société : sérialise l'ajout au journal de garde (chaînage). */
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from Company c where c.id = :id")
    Optional<Company> lockById(@org.springframework.data.repository.query.Param("id") Long id);
}
