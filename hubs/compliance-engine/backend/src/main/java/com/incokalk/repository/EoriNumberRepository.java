package com.incokalk.repository;

import com.incokalk.model.EoriNumber;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EoriNumberRepository extends JpaRepository<EoriNumber, UUID> {
    List<EoriNumber> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
    Page<EoriNumber> findByCompanyIdOrderByCreatedAtDesc(UUID companyId, Pageable pageable);
    Optional<EoriNumber> findByCompanyIdAndIsDefaultTrue(UUID companyId);
    Optional<EoriNumber> findByCompanyIdAndEori(UUID companyId, String eori);
    boolean existsByCompanyIdAndEori(UUID companyId, String eori);
    long countByCompanyId(UUID companyId);
}
