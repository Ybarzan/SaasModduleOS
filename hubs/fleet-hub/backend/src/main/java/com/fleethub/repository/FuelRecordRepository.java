package com.fleethub.repository;

import com.fleethub.model.FuelRecord;
import com.fleethub.model.Truck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FuelRecordRepository extends JpaRepository<FuelRecord, Long> {

    Optional<FuelRecord> findByIdAndCompanyId(Long id, Long companyId);

    List<FuelRecord> findByTruckAndDateBetween(Truck truck, LocalDate from, LocalDate to);

    List<FuelRecord> findByTruckAndDateAfter(Truck truck, LocalDate from);

    List<FuelRecord> findByTruckIdAndDate(Long truckId, LocalDate date);

    @Query("select f from FuelRecord f join fetch f.truck where f.company.id = :companyId order by f.date desc")
    List<FuelRecord> findAllFetch(@Param("companyId") Long companyId);

    void deleteByTruck(Truck truck);
    void deleteByCompany_Id(Long companyId);
}
