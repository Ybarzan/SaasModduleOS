package com.fleethub.repository;

import com.fleethub.model.Carrier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CarrierRepository extends JpaRepository<Carrier, UUID> {

    Optional<Carrier> findByIdAndCompanyId(UUID id, Long companyId);

    List<Carrier> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    Page<Carrier> findByCompanyIdOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    List<Carrier> findByCompanyIdAndIsActiveTrue(Long companyId);

    boolean existsByCompanyIdAndCodeIgnoreCase(Long companyId, String code);

    long countByCompanyId(Long companyId);

    long countByCompanyIdAndIsActiveTrue(Long companyId);
}
