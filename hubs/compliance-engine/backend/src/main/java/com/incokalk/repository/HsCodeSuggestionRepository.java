package com.incokalk.repository;

import com.incokalk.model.HsCodeSuggestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface HsCodeSuggestionRepository extends JpaRepository<HsCodeSuggestion, UUID> {

    List<HsCodeSuggestion> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    long countByCompanyId(UUID companyId);
}
