package com.incokalk.repository;

import com.incokalk.model.EmailIntakeLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmailIntakeLogRepository extends JpaRepository<EmailIntakeLog, UUID> {

    List<EmailIntakeLog> findByMailbox_IdOrderByStartedAtDesc(UUID mailboxId);

    List<EmailIntakeLog> findByMailbox_Company_IdOrderByStartedAtDesc(UUID companyId);
}
