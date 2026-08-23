package com.incokalk.repository;

import com.incokalk.model.EmailIntake;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmailIntakeRepository extends JpaRepository<EmailIntake, UUID> {

    List<EmailIntake> findByMatchedCompanyIdOrderByReceivedAtDesc(UUID companyId);

    List<EmailIntake> findByStatus(EmailIntake.IntakeStatus status);

    long countByStatus(EmailIntake.IntakeStatus status);
}
