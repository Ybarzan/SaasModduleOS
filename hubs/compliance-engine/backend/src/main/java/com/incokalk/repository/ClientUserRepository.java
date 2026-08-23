package com.incokalk.repository;

import com.incokalk.model.ClientUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientUserRepository extends JpaRepository<ClientUser, UUID> {
    Optional<ClientUser> findByEmailAndCompanyId(String email, UUID companyId);
    Optional<ClientUser> findByEmail(String email);
    Optional<ClientUser> findByIdAndCompanyId(UUID id, UUID companyId);
    List<ClientUser> findByCompanyIdOrderByCreatedAtDesc(UUID companyId);
    long countByCompanyId(UUID companyId);
    long countByCompanyIdAndActive(UUID companyId, boolean active);
}
