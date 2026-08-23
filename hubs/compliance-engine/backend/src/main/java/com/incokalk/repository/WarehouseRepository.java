package com.incokalk.repository;

import com.incokalk.model.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    List<Warehouse> findByCompanyId(UUID companyId);

    List<Warehouse> findByCompanyIdAndIsActiveTrue(UUID companyId);

    List<Warehouse> findByCompanyIdAndBranchId(UUID companyId, UUID branchId);

    Optional<Warehouse> findByCompanyIdAndId(UUID companyId, UUID id);
}
