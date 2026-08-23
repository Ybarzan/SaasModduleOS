package com.incokalk.repository;

import com.incokalk.model.VatRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VatRateRepository extends JpaRepository<VatRate, UUID> {
    List<VatRate> findByCountryCodeAndIsActiveTrue(String countryCode);
    List<VatRate> findByCountryCodeAndRateTypeAndIsActiveTrue(String countryCode, VatRate.RateType rateType);
    Optional<VatRate> findFirstByCountryCodeAndRateTypeAndIsActiveTrueOrderByValidFromDesc(String countryCode, VatRate.RateType rateType);
    List<VatRate> findByRateTypeAndIsActiveTrue(VatRate.RateType rateType);
}
