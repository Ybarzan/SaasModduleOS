package com.fleethub.repository;

import com.fleethub.model.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);

    List<AppUser> findByCompanyId(Long companyId);

    Optional<AppUser> findByIdAndCompanyId(Long id, Long companyId);

    void deleteByCompany_Id(Long companyId);

    Optional<AppUser> findByInviteToken(String inviteToken);

    Optional<AppUser> findByResetToken(String resetToken);

    long countByCompanyId(Long companyId);

    long countByCompanyIdAndRoleAndEnabled(Long companyId, String role, boolean enabled);
}
