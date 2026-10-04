package com.fleethub.repository;

import com.fleethub.model.Site;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SiteRepository extends JpaRepository<Site, Long> {

    List<Site> findByCompanyIdOrderByNameAsc(Long companyId);

    Optional<Site> findByIdAndCompanyId(Long id, Long companyId);

    void deleteByCompany_Id(Long companyId);
}
