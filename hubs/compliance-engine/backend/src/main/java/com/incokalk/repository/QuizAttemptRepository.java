package com.incokalk.repository;

import com.incokalk.model.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, UUID> {

    List<QuizAttempt> findByEnrollmentIdOrderByAttemptNumberDesc(UUID enrollmentId);

    Optional<QuizAttempt> findTopByEnrollmentIdOrderByAttemptNumberDesc(UUID enrollmentId);

    int countByEnrollmentId(UUID enrollmentId);

    List<QuizAttempt> findByModuleIdAndCompanyId(UUID moduleId, UUID companyId);

    long countByModuleIdAndCompanyIdAndPassedTrue(UUID moduleId, UUID companyId);
}
