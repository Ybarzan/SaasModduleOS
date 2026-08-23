package com.fleethub.repository;

import com.fleethub.model.Driver;
import com.fleethub.model.DriverTruckAssignment;
import com.fleethub.model.Truck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AssignmentRepository extends JpaRepository<DriverTruckAssignment, Long> {

    @Query("select a from DriverTruckAssignment a join fetch a.driver join fetch a.truck where a.active = true and a.company.id = :companyId")
    List<DriverTruckAssignment> findByActiveTrue(@Param("companyId") Long companyId);

    @Query("select a from DriverTruckAssignment a join fetch a.driver join fetch a.truck where a.company.id = :companyId order by a.startDate desc")
    List<DriverTruckAssignment> findAllFetch(@Param("companyId") Long companyId);

    Optional<DriverTruckAssignment> findByIdAndCompanyId(Long id, Long companyId);

    void deleteByDriver(Driver driver);

    void deleteByTruck(Truck truck);

    void deleteByCompany_Id(Long companyId);
}
