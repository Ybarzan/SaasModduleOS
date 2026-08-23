package com.incokalk.repository;

import com.incokalk.model.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {

    List<ApprovalRequest> findByCompanyIdOrderByRequestedAtDesc(UUID companyId);

    Optional<ApprovalRequest> findByCompanyIdAndId(UUID companyId, UUID id);

    List<ApprovalRequest> findByCompanyIdAndStatus(UUID companyId, ApprovalRequest.ApprovalStatus status);

    long countByCompanyIdAndStatus(UUID companyId, ApprovalRequest.ApprovalStatus status);

    List<ApprovalRequest> findByCompanyIdAndEntityTypeAndEntityId(UUID companyId, ApprovalRequest.EntityType entityType, UUID entityId);

    Optional<ApprovalRequest> findByCompanyIdAndEntityTypeAndEntityIdAndStatusIn(UUID companyId, ApprovalRequest.EntityType entityType, UUID entityId, List<ApprovalRequest.ApprovalStatus> statuses);
}
