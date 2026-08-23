package com.incokalk.repository;

import com.incokalk.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyRepository extends JpaRepository<Company, UUID> {
    Optional<Company> findBySlug(String slug);
    boolean existsBySlug(String slug);
    Optional<Company> findByReferralCode(String referralCode);
    boolean existsByReferralCode(String referralCode);
}
