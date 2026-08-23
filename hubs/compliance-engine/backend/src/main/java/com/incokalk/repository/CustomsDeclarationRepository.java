package com.incokalk.repository;

import com.incokalk.model.CustomsDeclaration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomsDeclarationRepository extends JpaRepository<CustomsDeclaration, UUID> {

    List<CustomsDeclaration> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Page<CustomsDeclaration> findByCompanyIdOrderByCreatedAtDesc(UUID companyId, Pageable pageable);

    Optional<CustomsDeclaration> findByCompanyIdAndDeclarationNumber(UUID companyId, String declarationNumber);

    List<CustomsDeclaration> findByCompanyIdAndStatus(UUID companyId, CustomsDeclaration.DeclarationStatus status);

    long countByCompanyId(UUID companyId);

    long countByCompanyIdAndStatus(UUID companyId, CustomsDeclaration.DeclarationStatus status);

    Optional<CustomsDeclaration> findByCompanyIdAndId(UUID companyId, UUID id);
}
