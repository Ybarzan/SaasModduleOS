package com.fleethub.repository;

import com.fleethub.model.Driver;
import com.fleethub.model.Trip;
import com.fleethub.model.Truck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TripRepository extends JpaRepository<Trip, Long> {

    Optional<Trip> findByIdAndCompanyId(Long id, Long companyId);

    List<Trip> findByDriverAndStartTimeBetween(Driver driver, LocalDateTime from, LocalDateTime to);

    List<Trip> findByDriverAndStartTimeAfter(Driver driver, LocalDateTime from);

    List<Trip> findByTruckAndStartTimeBetween(Truck truck, LocalDateTime from, LocalDateTime to);

    @Query("select coalesce(sum(t.distanceKm),0.0) from Trip t where t.driver = :driver and t.startTime >= :from and t.startTime < :to")
    double sumDistanceByDriverBetween(@Param("driver") Driver driver, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select coalesce(sum(t.distanceKm),0.0) from Trip t where t.truck = :truck and t.startTime >= :from and t.startTime < :to")
    double sumDistanceByTruckBetween(@Param("truck") Truck truck, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select t from Trip t join fetch t.driver join fetch t.truck where t.company.id = :companyId order by t.startTime desc")
    List<Trip> findAllFetch(@Param("companyId") Long companyId);

    void deleteByDriver(Driver driver);

    void deleteByTruck(Truck truck);

    void deleteByCompany_Id(Long companyId);
}
