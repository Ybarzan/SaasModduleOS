package com.incokalk.repository;

import com.incokalk.model.EventOutbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventOutboxRepository extends JpaRepository<EventOutbox, java.util.UUID> {

    List<EventOutbox> findByStatusOrderByCreatedAtAsc(EventOutbox.Status status, Pageable pageable);
}
