package com.fleethub.repository;

import com.fleethub.model.Site;
import com.fleethub.model.TourStop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface TourStopRepository extends JpaRepository<TourStop, Long> {

    List<TourStop> findByCompanyIdAndTour_DateBetween(Long companyId, LocalDate from, LocalDate to);

    boolean existsBySite(Site site);

    void deleteByCompany_Id(Long companyId);
}
