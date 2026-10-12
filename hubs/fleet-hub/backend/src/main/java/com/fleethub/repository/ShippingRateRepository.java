package com.fleethub.repository;

import com.fleethub.model.ShippingRate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ShippingRateRepository extends JpaRepository<ShippingRate, UUID> {

    List<ShippingRate> findByCarrier_IdAndCompany_IdOrderByCreatedAtDesc(UUID carrierId, Long companyId);

    List<ShippingRate> findByCompany_IdOrderByCreatedAtDesc(Long companyId);

    Page<ShippingRate> findByCompany_IdOrderByCreatedAtDesc(Long companyId, Pageable pageable);

    List<ShippingRate> findByCompany_Id(Long companyId);

    List<ShippingRate> findByCompany_IdAndOriginCountryAndDestinationCountryAndTransportModeAndIsActiveTrue(
            Long companyId, String originCountry, String destinationCountry, String transportMode);

    List<ShippingRate> findByCompany_IdAndOriginCountryAndDestinationCountry(
            Long companyId, String originCountry, String destinationCountry);

    @Query("SELECT r FROM ShippingRate r " +
           "WHERE r.company.id = :companyId " +
           "  AND r.originCountry = :originCountry " +
           "  AND r.destinationCountry = :destinationCountry " +
           "  AND r.transportMode = :transportMode " +
           "  AND r.isActive = true " +
           "  AND (cast(:weightKg as double) IS NULL OR (r.minWeightKg IS NULL OR r.minWeightKg <= cast(:weightKg as double))) " +
           "  AND (cast(:weightKg as double) IS NULL OR (r.maxWeightKg IS NULL OR r.maxWeightKg >= cast(:weightKg as double))) " +
           "  AND (cast(:date as timestamp) IS NULL OR (r.validFrom IS NULL OR r.validFrom <= cast(:date as timestamp))) " +
           "  AND (cast(:date as timestamp) IS NULL OR (r.validUntil IS NULL OR r.validUntil >= cast(:date as timestamp))) " +
           "ORDER BY r.baseRate ASC")
    List<ShippingRate> findMatchingRates(@Param("companyId") Long companyId,
                                          @Param("originCountry") String originCountry,
                                          @Param("destinationCountry") String destinationCountry,
                                          @Param("transportMode") String transportMode,
                                          @Param("weightKg") Double weightKg,
                                          @Param("date") LocalDateTime date);
}
