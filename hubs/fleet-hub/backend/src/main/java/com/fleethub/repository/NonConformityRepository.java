package com.fleethub.repository;

import com.fleethub.model.NonConformity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface NonConformityRepository extends JpaRepository<NonConformity, Long> {

    Optional<NonConformity> findByIdAndCompanyId(Long id, Long companyId);

    List<NonConformity> findByCompanyIdAndTourIdOrderByDetectedAtAsc(Long companyId, Long tourId);

    List<NonConformity> findByCompanyIdAndTourIdIn(Long companyId, Collection<Long> tourIds);

    @Query("select n from NonConformity n where n.companyId = :companyId "
            + "and n.detectedAt >= :from and n.detectedAt < :to order by n.detectedAt desc")
    List<NonConformity> findInPeriod(@Param("companyId") Long companyId, @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);

    List<NonConformity> findByCompanyIdAndStatusNotOrderByDetectedAtDesc(Long companyId, NonConformity.Status status);

    long countByCompanyIdAndStatusNot(Long companyId, NonConformity.Status status);

    boolean existsByCompanyIdAndTourIdAndTourStopIdAndType(Long companyId, Long tourId, Long tourStopId,
                                                           NonConformity.Type type);

    boolean existsByCompanyIdAndTourIdAndType(Long companyId, Long tourId, NonConformity.Type type);

    @Modifying
    @Query("delete from NonConformity n where n.companyId = :companyId")
    void deleteByCompanyId(@Param("companyId") Long companyId);
}
