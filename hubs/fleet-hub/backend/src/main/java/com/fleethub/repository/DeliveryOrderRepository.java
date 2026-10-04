package com.fleethub.repository;

import com.fleethub.model.DeliveryOrder;
import com.fleethub.model.Site;
import com.fleethub.model.Tour;
import com.fleethub.model.TourStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DeliveryOrderRepository extends JpaRepository<DeliveryOrder, Long> {

    List<DeliveryOrder> findByCompanyIdAndDateOrderByIdAsc(Long companyId, LocalDate date);

    List<DeliveryOrder> findByCompanyIdAndDateAndStatusOrderByIdAsc(Long companyId, LocalDate date,
                                                                    DeliveryOrder.OrderStatus status);

    Optional<DeliveryOrder> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsBySite(Site site);

    /** Remet « à planifier » les commandes d'une tournée supprimée ou d'un arrêt retiré. */
    @Modifying(flushAutomatically = true)
    @Query("update DeliveryOrder o set o.tourStop = null, o.status = com.fleethub.model.DeliveryOrder.OrderStatus.A_PLANIFIER where o.tourStop.id in (select s.id from TourStop s where s.tour = :tour)")
    int releaseByTour(@Param("tour") Tour tour);

    @Modifying(flushAutomatically = true)
    @Query("update DeliveryOrder o set o.tourStop = null, o.status = com.fleethub.model.DeliveryOrder.OrderStatus.A_PLANIFIER where o.tourStop = :stop")
    int releaseByStop(@Param("stop") TourStop stop);

    void deleteByCompany_Id(Long companyId);
}
