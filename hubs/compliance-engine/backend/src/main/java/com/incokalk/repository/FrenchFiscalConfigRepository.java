package com.incokalk.repository;

import com.incokalk.model.FrenchFiscalConfig;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FrenchFiscalConfigRepository extends JpaRepository<FrenchFiscalConfig, UUID> {
    Optional<FrenchFiscalConfig> findByCompanyId(UUID companyId);
}
