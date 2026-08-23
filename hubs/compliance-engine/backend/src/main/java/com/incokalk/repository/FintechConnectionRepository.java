package com.incokalk.repository;

import com.incokalk.model.FintechConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FintechConnectionRepository extends JpaRepository<FintechConnection, UUID> {

    List<FintechConnection> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);

    Optional<FintechConnection> findByCompanyIdAndId(UUID companyId, UUID id);
}
