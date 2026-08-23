package com.fleethub.repository;

import com.fleethub.model.Driver;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DriverRepository extends JpaRepository<Driver, Long> {

    Optional<Driver> findByIdAndCompanyId(Long id, Long companyId);

    Optional<Driver> findByLicenseNumberAndCompanyId(String licenseNumber, Long companyId);

    List<Driver> findByCompanyId(Long companyId);

    long countByCompanyId(Long companyId);

    /** Non scopé — réservé à la couche d'intégration externe (voir INTEGRATION.md). */
    Optional<Driver> findByLicenseNumber(String licenseNumber);

    void deleteByCompany_Id(Long companyId);
}
