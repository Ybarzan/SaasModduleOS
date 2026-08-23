package com.fleethub.repository;

import com.fleethub.model.MaintenanceRecord;
import com.fleethub.model.Truck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface MaintenanceRepository extends JpaRepository<MaintenanceRecord, Long> {

    List<MaintenanceRecord> findByTruckAndScheduledDateBetween(Truck truck, LocalDate from, LocalDate to);

    List<MaintenanceRecord> findByTruckAndScheduledDateAfter(Truck truck, LocalDate from);

    @Query("select m from MaintenanceRecord m join fetch m.truck where m.company.id = :companyId order by m.scheduledDate desc")
    List<MaintenanceRecord> findAllFetch(@Param("companyId") Long companyId);

    void deleteByTruck(Truck truck);
    void deleteByCompany_Id(Long companyId);
}
