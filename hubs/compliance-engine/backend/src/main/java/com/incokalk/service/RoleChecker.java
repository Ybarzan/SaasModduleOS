package com.incokalk.service;

import com.incokalk.model.CompanyRole;
import com.incokalk.repository.CompanyRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RoleChecker {

    private final CompanyRoleRepository roleRepo;

    public boolean hasRole(UUID userId, UUID companyId, CompanyRole.Role required) {
        return roleRepo.findByCompanyIdAndUserId(companyId, userId)
            .map(r -> r.getRole().ordinal() <= required.ordinal())
            .orElse(false);
    }

    public boolean hasRole(UUID userId, UUID companyId, CompanyRole.Role... allowed) {
        return roleRepo.findByCompanyIdAndUserId(companyId, userId)
            .map(r -> List.of(allowed).contains(r.getRole()))
            .orElse(false);
    }

    public CompanyRole.Role getRole(UUID userId, UUID companyId) {
        return roleRepo.findByCompanyIdAndUserId(companyId, userId)
            .map(CompanyRole::getRole)
            .orElse(null);
    }
}
