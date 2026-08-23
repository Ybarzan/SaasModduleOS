package com.fleethub.repository;

import com.fleethub.model.CostRecord;
import com.fleethub.model.Driver;
import com.fleethub.model.Truck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.YearMonth;
import java.util.List;

public interface CostRecordRepository extends JpaRepository<CostRecord, Long> {

    List<CostRecord> findByTruckAndDriver(Truck truck, Driver driver);

    List<CostRecord> findByTruck(Truck truck);

    List<CostRecord> findByTruckAndDriverAndBillingMonthAfter(Truck truck, Driver driver, YearMonth month);

    @Query("select c from CostRecord c join fetch c.truck join fetch c.driver where c.company.id = :companyId order by c.billingMonth desc")
    List<CostRecord> findAllFetch(@Param("companyId") Long companyId);

    void deleteByDriver(Driver driver);

    void deleteByTruck(Truck truck);

    void deleteByCompany_Id(Long companyId);
}
