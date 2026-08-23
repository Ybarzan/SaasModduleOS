package com.incokalk.repository;

import com.incokalk.model.DebDeclaration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DebDeclarationRepository extends JpaRepository<DebDeclaration, UUID> {
    List<DebDeclaration> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
    Optional<DebDeclaration> findByCompanyIdAndId(UUID companyId, UUID id);
    List<DebDeclaration> findByCompanyIdAndPeriod(UUID companyId, String period);
    long countByCompanyId(UUID companyId);
    long countByCompanyIdAndStatus(UUID companyId, DebDeclaration.DebStatus status);
    Optional<DebDeclaration> findByCompanyIdAndDeclarationNumber(UUID companyId, String declarationNumber);
}
