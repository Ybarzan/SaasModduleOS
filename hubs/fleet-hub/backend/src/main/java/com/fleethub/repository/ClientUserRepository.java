package com.fleethub.repository;

import com.fleethub.model.ClientUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientUserRepository extends JpaRepository<ClientUser, UUID> {
    Optional<ClientUser> findByEmailAndCompanyId(String email, Long companyId);
    Optional<ClientUser> findByEmail(String email);
    Optional<ClientUser> findByIdAndCompanyId(UUID id, Long companyId);
    List<ClientUser> findByCompanyIdOrderByCreatedAtDesc(Long companyId);
    long countByCompanyId(Long companyId);
    long countByCompanyIdAndActive(Long companyId, boolean active);
}
