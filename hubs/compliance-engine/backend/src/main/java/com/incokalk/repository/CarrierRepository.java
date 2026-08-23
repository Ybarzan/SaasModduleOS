package com.incokalk.repository;

import com.incokalk.model.Carrier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CarrierRepository extends JpaRepository<Carrier, UUID> {

    Optional<Carrier> findByIdAndCompanyId(UUID id, UUID companyId);

    List<Carrier> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Page<Carrier> findByCompanyIdOrderByCreatedAtDesc(UUID companyId, Pageable pageable);

    List<Carrier> findByCompanyIdAndIsActiveTrue(UUID companyId);

    boolean existsByCompanyIdAndCodeIgnoreCase(UUID companyId, String code);

    long countByCompanyId(UUID companyId);

    long countByCompanyIdAndIsActiveTrue(UUID companyId);
}
