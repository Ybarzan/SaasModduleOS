package com.fleethub.repository;

import com.fleethub.model.Site;
import com.fleethub.model.TourStop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TourStopRepository extends JpaRepository<TourStop, Long> {

    List<TourStop> findByCompanyIdAndTour_DateBetween(Long companyId, LocalDate from, LocalDate to);

    boolean existsBySite(Site site);

    /** Derniers signataires par site (plus récents d'abord) : [siteId, signedBy]. */
    @org.springframework.data.jpa.repository.Query("select s.site.id, s.signedBy from TourStop s where s.company.id = :companyId "
            + "and s.site.id in :siteIds and s.signedBy is not null order by s.completedAt desc")
    List<Object[]> recentSigners(@org.springframework.data.repository.query.Param("companyId") Long companyId,
                                 @org.springframework.data.repository.query.Param("siteIds") java.util.Collection<Long> siteIds);

    void deleteByCompany_Id(Long companyId);
}
