package com.incokalk.repository;

import com.incokalk.model.SanctionedEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SanctionedEntityRepository extends JpaRepository<SanctionedEntity, UUID> {

    List<SanctionedEntity> findByIsActiveTrue();

    List<SanctionedEntity> findByNameContainingIgnoreCaseAndIsActiveTrue(String name);

    List<SanctionedEntity> findByCountryCodeAndIsActiveTrue(String countryCode);

    List<SanctionedEntity> findByListSourceAndIsActiveTrue(String listSource);

    Optional<SanctionedEntity> findByEntryIdAndListSource(String entryId, String listSource);
}
