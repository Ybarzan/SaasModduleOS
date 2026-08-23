package com.incokalk.repository;

import com.incokalk.model.CourseEnrollment;
import com.incokalk.model.CourseEnrollment.Status;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, UUID> {

    List<CourseEnrollment> findByCompanyIdAndUserId(UUID companyId, UUID userId);

    Optional<CourseEnrollment> findByCompanyIdAndUserIdAndModuleId(UUID companyId, UUID userId, UUID moduleId);

    List<CourseEnrollment> findByCompanyIdAndStatus(UUID companyId, Status status);

    long countByCompanyId(UUID companyId);

    long countByCompanyIdAndStatus(UUID companyId, Status status);

    @Query("SELECT e.moduleId, COUNT(e) FROM CourseEnrollment e WHERE e.companyId = :companyId GROUP BY e.moduleId ORDER BY COUNT(e) DESC")
    List<Object[]> findTopModulesByCompanyId(@Param("companyId") UUID companyId);

    List<CourseEnrollment> findByCompanyId(UUID companyId);
}
