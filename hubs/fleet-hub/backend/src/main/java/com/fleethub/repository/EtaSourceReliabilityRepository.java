package com.fleethub.repository;

import com.fleethub.model.EtaSourceReliability;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EtaSourceReliabilityRepository extends JpaRepository<EtaSourceReliability, UUID> {

    List<EtaSourceReliability> findByCompanyId(Long companyId);

    Optional<EtaSourceReliability> findByCompanyIdAndCarrierIdAndOriginCountryAndDestinationCountryAndTransportMode(
            Long companyId, UUID carrierId, String originCountry, String destinationCountry, String transportMode);
}
