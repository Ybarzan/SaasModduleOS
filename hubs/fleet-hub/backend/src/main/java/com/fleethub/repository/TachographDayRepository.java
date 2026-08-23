package com.fleethub.repository;

import com.fleethub.model.Driver;
import com.fleethub.model.TachographDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface TachographDayRepository extends JpaRepository<TachographDay, Long> {

    Optional<TachographDay> findByIdAndCompanyId(Long id, Long companyId);

    List<TachographDay> findByDriverAndDateBetween(Driver driver, LocalDate from, LocalDate to);

    List<TachographDay> findByDriverAndDateAfter(Driver driver, LocalDate from);

    Optional<TachographDay> findByDriverIdAndDate(Long driverId, LocalDate date);

    @Query("select t from TachographDay t join fetch t.driver where t.company.id = :companyId order by t.date desc")
    List<TachographDay> findAllFetch(@Param("companyId") Long companyId);

    @Query("select count(t) from TachographDay t where t.company.id = :companyId "
            + "and t.date between :from and :to and t.compliant = false")
    long countNonCompliantBetween(@Param("companyId") Long companyId,
                                  @Param("from") LocalDate from,
                                  @Param("to") LocalDate to);

    void deleteByDriver(Driver driver);
    void deleteByCompany_Id(Long companyId);
}
