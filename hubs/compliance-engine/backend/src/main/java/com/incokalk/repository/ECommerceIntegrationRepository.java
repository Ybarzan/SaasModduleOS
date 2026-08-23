package com.incokalk.repository;

import com.incokalk.model.ECommerceIntegration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ECommerceIntegrationRepository extends JpaRepository<ECommerceIntegration, UUID> {

    List<ECommerceIntegration> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<ECommerceIntegration> findByPlatformAndCompanyId(ECommerceIntegration.Platform platform, UUID companyId);

    List<ECommerceIntegration> findByCompanyIdAndIsActiveTrue(UUID companyId);
}
