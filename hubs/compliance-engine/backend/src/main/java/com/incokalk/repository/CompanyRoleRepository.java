package com.incokalk.repository;

import com.incokalk.model.CompanyRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CompanyRoleRepository extends JpaRepository<CompanyRole, UUID> {
    List<CompanyRole> findByCompanyId(UUID companyId);
    Optional<CompanyRole> findByCompanyIdAndUserId(UUID companyId, UUID userId);
    boolean existsByCompanyIdAndUserId(UUID companyId, UUID userId);
    long countByCompanyIdAndRole(UUID companyId, CompanyRole.Role role);
    long countByCustomRole_Id(UUID customRoleId);
    List<CompanyRole> findByCompanyIdAndRole(UUID companyId, CompanyRole.Role role);

    @Modifying
    @Query(value = "INSERT INTO company_roles (id, company_id, user_id, role, created_at) VALUES (:id, :companyId, :userId, :role, CURRENT_TIMESTAMP)", nativeQuery = true)
    void insertNative(@Param("id") UUID id, @Param("companyId") UUID companyId, @Param("userId") UUID userId, @Param("role") String role);
}
