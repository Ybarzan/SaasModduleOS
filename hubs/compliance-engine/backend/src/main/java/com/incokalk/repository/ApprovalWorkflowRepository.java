package com.incokalk.repository;

import com.incokalk.model.ApprovalWorkflow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalWorkflowRepository extends JpaRepository<ApprovalWorkflow, UUID> {

    List<ApprovalWorkflow> findByCompanyIdAndIsActiveTrue(UUID companyId);

    Optional<ApprovalWorkflow> findByCompanyIdAndId(UUID companyId, UUID id);

    Optional<ApprovalWorkflow> findByCompanyIdAndEntityTypeAndIsActiveTrue(UUID companyId, ApprovalWorkflow.EntityType entityType);
}
