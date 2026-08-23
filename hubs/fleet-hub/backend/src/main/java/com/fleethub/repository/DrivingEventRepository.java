package com.fleethub.repository;

import com.fleethub.model.Driver;
import com.fleethub.model.DrivingEvent;
import com.fleethub.model.Truck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface DrivingEventRepository extends JpaRepository<DrivingEvent, Long> {

    List<DrivingEvent> findByDriverAndTimestampBetween(Driver driver, LocalDateTime from, LocalDateTime to);

    List<DrivingEvent> findByDriverAndTimestampAfter(Driver driver, LocalDateTime from);

    List<DrivingEvent> findByTruckAndTimestampAfter(Truck truck, LocalDateTime from);

    List<DrivingEvent> findByTruckAndTimestampBetween(Truck truck, LocalDateTime from, LocalDateTime to);

    @Query("select e from DrivingEvent e join fetch e.driver join fetch e.truck where e.company.id = :companyId order by e.timestamp desc")
    List<DrivingEvent> findAllFetch(@Param("companyId") Long companyId);

    void deleteByDriver(Driver driver);

    void deleteByTruck(Truck truck);

    void deleteByCompany_Id(Long companyId);
}
