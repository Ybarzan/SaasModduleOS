package com.incokalk.repository;

import com.incokalk.model.OrchestrationSuggestion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrchestrationSuggestionRepository extends JpaRepository<OrchestrationSuggestion, UUID> {

    List<OrchestrationSuggestion> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Page<OrchestrationSuggestion> findByCompanyIdOrderByCreatedAtDesc(UUID companyId, Pageable pageable);

    List<OrchestrationSuggestion> findByCompanyIdAndStatusOrderByCreatedAtDesc(
            UUID companyId, OrchestrationSuggestion.Status status);

    Optional<OrchestrationSuggestion> findByIdAndCompanyId(UUID id, UUID companyId);
}
