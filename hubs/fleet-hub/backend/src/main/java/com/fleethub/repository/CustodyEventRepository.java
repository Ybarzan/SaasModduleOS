package com.fleethub.repository;

import com.fleethub.model.CustodyEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Journal de garde : lecture et ajout uniquement (aucune mise à jour). */
public interface CustodyEventRepository extends JpaRepository<CustodyEvent, Long> {

    Optional<CustodyEvent> findTopByCompanyIdOrderBySequenceNoDesc(Long companyId);

    List<CustodyEvent> findByCompanyIdOrderBySequenceNoAsc(Long companyId);

    List<CustodyEvent> findByCompanyIdAndTourIdOrderBySequenceNoAsc(Long companyId, Long tourId);

    @Query("select e from CustodyEvent e where e.companyId = :companyId "
            + "and e.occurredAt >= :from and e.occurredAt < :to order by e.sequenceNo desc")
    List<CustodyEvent> findInPeriod(@Param("companyId") Long companyId, @Param("from") LocalDateTime from,
                                    @Param("to") LocalDateTime to, Pageable page);

    boolean existsByCompanyIdAndTourId(Long companyId, Long tourId);

    long countByCompanyId(Long companyId);

    /** Effacement RGPD de la société (suppression du compte) uniquement. */
    @Modifying
    @Query("delete from CustodyEvent e where e.companyId = :companyId")
    void deleteByCompanyId(@Param("companyId") Long companyId);
}
