package com.incokalk.repository;

import com.incokalk.model.CargoInsuranceQuote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CargoInsuranceQuoteRepository extends JpaRepository<CargoInsuranceQuote, UUID> {

    List<CargoInsuranceQuote> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<CargoInsuranceQuote> findByCompanyIdAndId(UUID companyId, UUID id);
}
