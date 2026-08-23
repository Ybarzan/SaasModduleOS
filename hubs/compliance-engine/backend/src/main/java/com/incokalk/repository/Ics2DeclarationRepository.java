package com.incokalk.repository;

import com.incokalk.model.Ics2Declaration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface Ics2DeclarationRepository extends JpaRepository<Ics2Declaration, UUID> {
    List<Ics2Declaration> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
    Optional<Ics2Declaration> findByCompanyIdAndId(UUID companyId, UUID id);
    long countByCompanyIdAndStatus(UUID companyId, Ics2Declaration.Ics2Status status);
    Optional<Ics2Declaration> findByCompanyIdAndDeclarationNumber(UUID companyId, String declarationNumber);
}
