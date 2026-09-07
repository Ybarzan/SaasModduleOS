package com.fleethub.repository;

import com.fleethub.model.Driver;
import com.fleethub.model.PointageEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PointageEventRepository extends JpaRepository<PointageEvent, Long> {

    List<PointageEvent> findByDriverIdAndOccurredAtBetweenOrderByOccurredAtAsc(
            Long driverId, LocalDateTime from, LocalDateTime to);

    List<PointageEvent> findByDriverIdOrderByOccurredAtAsc(Long driverId);

    Optional<PointageEvent> findFirstByDriverIdOrderByOccurredAtDesc(Long driverId);

    @Query("select e from PointageEvent e where e.company.id = :companyId and e.driver.id = :driverId "
            + "and e.occurredAt between :from and :to order by e.occurredAt asc")
    List<PointageEvent> findForDriverBetween(@Param("companyId") Long companyId, @Param("driverId") Long driverId,
                                             @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    void deleteByDriver(Driver driver);

    void deleteByCompany_Id(Long companyId);
}
