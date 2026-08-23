package com.incokalk.repository;

import com.incokalk.model.Simulation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.UUID;

@Repository
public interface SimulationRepository extends JpaRepository<Simulation, UUID> {

    Page<Simulation> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    long countByUserId(UUID userId);

    @Query("SELECT COUNT(s) FROM Simulation s WHERE s.user.id = :userId AND s.createdAt >= CURRENT_DATE")
    long countTodayByUserId(UUID userId);

    long countByCompanyId(UUID companyId);

    long countByCompanyIdAndCreatedAtAfter(UUID companyId, LocalDateTime since);
}
