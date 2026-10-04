package com.fleethub.repository;

import com.fleethub.model.Driver;
import com.fleethub.model.Site;
import com.fleethub.model.Tour;
import com.fleethub.model.Truck;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TourRepository extends JpaRepository<Tour, Long> {

    Optional<Tour> findByIdAndCompanyId(Long id, Long companyId);

    List<Tour> findByCompanyIdAndDateBetweenOrderByDateAscPlannedStartAsc(Long companyId, LocalDate from, LocalDate to);

    List<Tour> findByCompanyIdAndDriverIdAndDateOrderByPlannedStartAsc(Long companyId, Long driverId, LocalDate date);

    @Modifying
    @Query("update Tour t set t.driver = null where t.driver = :driver")
    void detachDriver(@Param("driver") Driver driver);

    @Modifying
    @Query("update Tour t set t.truck = null where t.truck = :truck")
    void detachTruck(@Param("truck") Truck truck);

    @Modifying
    @Query("update Tour t set t.depot = null where t.depot = :site")
    void detachDepot(@Param("site") Site site);

    void deleteByCompany_Id(Long companyId);
}
