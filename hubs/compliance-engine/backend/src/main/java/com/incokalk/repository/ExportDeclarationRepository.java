package com.incokalk.repository;

import com.incokalk.model.ExportDeclaration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ExportDeclarationRepository extends JpaRepository<ExportDeclaration, UUID> {
    List<ExportDeclaration> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
    Optional<ExportDeclaration> findByCompanyIdAndId(UUID companyId, UUID id);
    long countByCompanyIdAndStatus(UUID companyId, ExportDeclaration.ExportStatus status);
    Optional<ExportDeclaration> findByCompanyIdAndDeclarationNumber(UUID companyId, String declarationNumber);
}
