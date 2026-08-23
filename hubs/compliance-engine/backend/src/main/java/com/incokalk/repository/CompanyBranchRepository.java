package com.incokalk.repository;

import com.incokalk.model.CompanyBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyBranchRepository extends JpaRepository<CompanyBranch, UUID> {

    List<CompanyBranch> findByParentCompanyIdAndIsActiveTrue(UUID parentCompanyId);

    List<CompanyBranch> findByBranchCompanyIdAndIsActiveTrue(UUID branchCompanyId);

    Optional<CompanyBranch> findByParentCompanyIdAndBranchCompanyId(UUID parentCompanyId, UUID branchCompanyId);

    boolean existsByParentCompanyIdAndBranchCompanyId(UUID parentCompanyId, UUID branchCompanyId);

    Optional<CompanyBranch> findByIdAndParentCompanyId(UUID id, UUID parentCompanyId);

    Optional<CompanyBranch> findByIdAndParentCompanyIdAndIsActiveTrue(UUID id, UUID parentCompanyId);

    long countByParentCompanyId(UUID parentCompanyId);

    List<CompanyBranch> findByParentCompanyId(UUID parentCompanyId);
}
