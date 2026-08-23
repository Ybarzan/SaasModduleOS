package com.incokalk.repository;

import com.incokalk.model.EmailMailbox;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmailMailboxRepository extends JpaRepository<EmailMailbox, UUID> {

    List<EmailMailbox> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<EmailMailbox> findByIdAndCompanyId(UUID id, UUID companyId);

    List<EmailMailbox> findByIsActiveTrue();
}
