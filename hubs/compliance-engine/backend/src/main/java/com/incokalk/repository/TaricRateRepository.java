package com.incokalk.repository;

import com.incokalk.model.TaricRate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaricRateRepository extends JpaRepository<TaricRate, UUID> {

    @Query("SELECT DISTINCT t.hsCode, t.description FROM TaricRate t " +
           "WHERE t.description IS NOT NULL AND t.description != '' " +
           "ORDER BY t.hsCode ASC")
    List<Object[]> findDistinctHsCodesWithDescriptions();

    @Query("SELECT t FROM TaricRate t WHERE t.hsCode = :hsCode " +
           "AND t.originCountry = :origin AND t.destinationCountry = :dest " +
           "AND t.validFrom <= :date AND (t.validTo IS NULL OR t.validTo >= :date) " +
           "ORDER BY t.isPrefential DESC, t.dutyRate ASC")
    List<TaricRate> findApplicableRates(
        @Param("hsCode") String hsCode,
        @Param("origin") String origin,
        @Param("dest") String dest,
        @Param("date") LocalDate date
    );

    @Query("SELECT t FROM TaricRate t WHERE t.hsCode = :hsCode " +
           "AND t.originCountry = :origin AND t.destinationCountry = :dest " +
           "AND t.isPrefential = true AND t.validFrom <= :date " +
           "AND (t.validTo IS NULL OR t.validTo >= :date)")
    List<TaricRate> findPrefentialRates(
        @Param("hsCode") String hsCode,
        @Param("origin") String origin,
        @Param("dest") String dest,
        @Param("date") LocalDate date
    );

    @Query("SELECT t FROM TaricRate t WHERE t.hsCode = :hsCode " +
           "AND t.originCountry = :origin AND t.destinationCountry = :dest " +
           "AND t.isPrefential = false AND t.validFrom <= :date " +
           "AND (t.validTo IS NULL OR t.validTo >= :date) " +
           "ORDER BY t.dutyRate ASC")
    List<TaricRate> findMFNRates(
        @Param("hsCode") String hsCode,
        @Param("origin") String origin,
        @Param("dest") String dest,
        @Param("date") LocalDate date
    );

    Optional<TaricRate> findFirstByHsCodeAndOriginCountryAndDestinationCountryAndIsPrefentialFalse(
        String hsCode, String originCountry, String destinationCountry
    );

    List<TaricRate> findByHsCodeAndOriginCountryAndDestinationCountry(
        String hsCode, String originCountry, String destinationCountry
    );

    @Query("SELECT t FROM TaricRate t WHERE t.hsCode = :hsCode " +
           "AND t.originCountry = :origin AND t.destinationCountry = :dest " +
           "AND t.isPrefential = :prefential")
    List<TaricRate> findPreferentialRates(
        @Param("hsCode") String hsCode,
        @Param("origin") String origin,
        @Param("dest") String dest,
        @Param("prefential") boolean prefential
    );

    List<TaricRate> findByHsCodeStartingWithAndDestinationCountry(String hsCodePrefix, String destCountry);

    long countByHsCodeStartingWith(String hsCodePrefix);

    @Query(value = "SELECT t FROM TaricRate t WHERE lower(t.description) LIKE CONCAT('%', lower(:keyword), '%') " +
            "AND t.destinationCountry = :dest " +
            "AND t.validFrom <= :date AND (t.validTo IS NULL OR t.validTo >= :date) " +
            "ORDER BY t.hsCode ASC, t.isPrefential DESC",
            nativeQuery = false)
    List<TaricRate> searchByKeyword(
        @Param("keyword") String keyword,
        @Param("dest") String dest,
        @Param("date") LocalDate date
    );

    @Query(value = "SELECT DISTINCT t.hsCode FROM TaricRate t " +
            "WHERE lower(t.description) LIKE CONCAT('%', lower(:keyword), '%') " +
            "AND t.destinationCountry = :dest",
            nativeQuery = false)
    List<String> findHsCodesByKeyword(
        @Param("keyword") String keyword,
        @Param("dest") String dest
    );

    @Query("SELECT DISTINCT t.hsCode, t.description FROM TaricRate t " +
           "WHERE t.hsCode IN :codes AND t.description IS NOT NULL AND t.description != ''")
    List<Object[]> findDescriptionsByCodes(@Param("codes") List<String> codes);
}
