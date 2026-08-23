package com.incokalk.repository;

import com.incokalk.model.DeniedPartyCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DeniedPartyCheckRepository extends JpaRepository<DeniedPartyCheck, UUID> {

    List<DeniedPartyCheck> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<DeniedPartyCheck> findByCompanyIdAndId(UUID companyId, UUID id);

    long countByCompanyIdAndResult(UUID companyId, DeniedPartyCheck.CheckResult result);

    List<DeniedPartyCheck> findByCompanyIdAndCheckedNameContainingIgnoreCase(UUID companyId, String name);
}
