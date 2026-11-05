package com.fleethub.repository;

import com.fleethub.model.OutboundEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface OutboundEventRepository extends JpaRepository<OutboundEvent, Long> {

    /** Événements à envoyer maintenant, du plus ancien au plus récent. */
    @Query("SELECT e FROM OutboundEvent e WHERE e.status = com.fleethub.model.OutboundEvent.Status.PENDING "
            + "AND e.nextAttemptAt <= :now ORDER BY e.id")
    List<OutboundEvent> findDue(LocalDateTime now, Pageable page);

    /** Un événement plus ancien de la même société attend encore : on garde l'ordre d'envoi. */
    boolean existsByCompanyIdAndStatusAndIdLessThan(Long companyId, OutboundEvent.Status status, Long id);

    long countByCompanyIdAndStatus(Long companyId, OutboundEvent.Status status);

    Optional<OutboundEvent> findFirstByCompanyIdAndStatusOrderByIdDesc(Long companyId, OutboundEvent.Status status);

    /** Tête de file d'une société : l'événement qui échoue et fait attendre les suivants. */
    Optional<OutboundEvent> findFirstByCompanyIdAndStatusOrderByIdAsc(Long companyId, OutboundEvent.Status status);

    List<OutboundEvent> findByCompanyIdAndStatus(Long companyId, OutboundEvent.Status status);

    @Modifying
    @Query("DELETE FROM OutboundEvent e WHERE e.status = com.fleethub.model.OutboundEvent.Status.SENT AND e.sentAt < :before")
    int deleteSentBefore(LocalDateTime before);

    void deleteByCompanyId(Long companyId);
}
