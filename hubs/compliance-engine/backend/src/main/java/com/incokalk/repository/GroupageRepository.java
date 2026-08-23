package com.incokalk.repository;

import com.incokalk.model.Groupage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupageRepository extends JpaRepository<Groupage, UUID> {

    List<Groupage> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<Groupage> findByCompanyIdAndId(UUID companyId, UUID id);

    long countByCompanyId(UUID companyId);
}
