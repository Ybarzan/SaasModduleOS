package com.incokalk.repository;

import com.incokalk.model.CompanyBranding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyBrandingRepository extends JpaRepository<CompanyBranding, UUID> {

    Optional<CompanyBranding> findByCompanyId(UUID companyId);

    Optional<CompanyBranding> findByCustomDomain(String customDomain);
}
