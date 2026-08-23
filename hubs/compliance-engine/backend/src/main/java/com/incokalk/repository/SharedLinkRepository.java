package com.incokalk.repository;

import com.incokalk.model.SharedLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SharedLinkRepository extends JpaRepository<SharedLink, UUID> {
    Optional<SharedLink> findByToken(String token);
    List<SharedLink> findByShipmentIdAndCompanyIdOrderByCreatedAtDesc(UUID shipmentId, UUID companyId);
    List<SharedLink> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
    long countByCompanyId(UUID companyId);
}
