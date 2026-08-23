package com.fleethub.repository;

import com.fleethub.model.ImportHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ImportHistoryRepository extends JpaRepository<ImportHistory, Long> {

    List<ImportHistory> findByCompanyIdOrderByImportedAtDesc(Long companyId);
}
