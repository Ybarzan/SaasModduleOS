package com.fleethub.repository;

import com.fleethub.model.Truck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TruckRepository extends JpaRepository<Truck, Long> {

    Optional<Truck> findByIdAndCompanyId(Long id, Long companyId);

    Optional<Truck> findByRegistrationAndCompanyId(String registration, Long companyId);

    List<Truck> findByCompanyId(Long companyId);

    long countByCompanyId(Long companyId);

    long countByCompanyIdAndActiveTrue(Long companyId);


    void deleteByCompany_Id(Long companyId);
}
