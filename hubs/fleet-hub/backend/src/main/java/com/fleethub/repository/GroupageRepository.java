package com.fleethub.repository;

import com.fleethub.model.Groupage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface GroupageRepository extends JpaRepository<Groupage, UUID> {

    List<Groupage> findByCompanyIdOrderByCreatedAtDesc(Long companyId);

    Optional<Groupage> findByCompanyIdAndId(Long companyId, UUID id);

    long countByCompanyId(Long companyId);
}
