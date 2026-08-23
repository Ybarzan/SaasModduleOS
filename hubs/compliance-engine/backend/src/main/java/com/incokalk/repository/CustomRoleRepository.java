package com.incokalk.repository;

import com.incokalk.model.CustomRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomRoleRepository extends JpaRepository<CustomRole, UUID> {

    List<CustomRole> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<CustomRole> findByIdAndCompanyId(UUID id, UUID companyId);

    boolean existsByCompanyIdAndNameIgnoreCase(UUID companyId, String name);
}
