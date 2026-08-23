package com.incokalk.service;

import com.incokalk.model.CompanyRole;
import com.incokalk.repository.CompanyRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("RoleChecker — Tests unitaires")
class RoleCheckerTest {

    CompanyRoleRepository roleRepo;
    RoleChecker checker;
    UUID userId;
    UUID companyId;

    @BeforeEach
    void setUp() {
        roleRepo = mock(CompanyRoleRepository.class);
        checker = new RoleChecker(roleRepo);
        userId = UUID.randomUUID();
        companyId = UUID.randomUUID();
    }

    @Test
    @DisplayName("hasRole → OWNER satisfait OWNER")
    void hasRole_ownerMeetsOwner() {
        CompanyRole cr = new CompanyRole();
        cr.setRole(CompanyRole.Role.OWNER);
        when(roleRepo.findByCompanyIdAndUserId(companyId, userId)).thenReturn(Optional.of(cr));
        assertThat(checker.hasRole(userId, companyId, CompanyRole.Role.OWNER)).isTrue();
    }

    @Test
    @DisplayName("hasRole → ADMIN ne satisfait pas OWNER")
    void hasRole_adminNotOwner() {
        CompanyRole cr = new CompanyRole();
        cr.setRole(CompanyRole.Role.ADMIN);
        when(roleRepo.findByCompanyIdAndUserId(companyId, userId)).thenReturn(Optional.of(cr));
        assertThat(checker.hasRole(userId, companyId, CompanyRole.Role.OWNER)).isFalse();
    }

    @Test
    @DisplayName("hasRole → ADMIN satisfait ADMIN")
    void hasRole_adminMeetsAdmin() {
        CompanyRole cr = new CompanyRole();
        cr.setRole(CompanyRole.Role.ADMIN);
        when(roleRepo.findByCompanyIdAndUserId(companyId, userId)).thenReturn(Optional.of(cr));
        assertThat(checker.hasRole(userId, companyId, CompanyRole.Role.ADMIN)).isTrue();
    }

    @Test
    @DisplayName("hasRole → pas de role → false")
    void hasRole_noRole() {
        when(roleRepo.findByCompanyIdAndUserId(companyId, userId)).thenReturn(Optional.empty());
        assertThat(checker.hasRole(userId, companyId, CompanyRole.Role.USER)).isFalse();
    }

    @Test
    @DisplayName("hasRole (multiple) → USER dans {ADMIN,USER} → true")
    void hasRole_multipleAllowed() {
        CompanyRole cr = new CompanyRole();
        cr.setRole(CompanyRole.Role.USER);
        when(roleRepo.findByCompanyIdAndUserId(companyId, userId)).thenReturn(Optional.of(cr));
        assertThat(checker.hasRole(userId, companyId, CompanyRole.Role.ADMIN, CompanyRole.Role.USER)).isTrue();
    }

    @Test
    @DisplayName("hasRole (multiple) → USER pas dans {OWNER,ADMIN} → false")
    void hasRole_multipleDenied() {
        CompanyRole cr = new CompanyRole();
        cr.setRole(CompanyRole.Role.USER);
        when(roleRepo.findByCompanyIdAndUserId(companyId, userId)).thenReturn(Optional.of(cr));
        assertThat(checker.hasRole(userId, companyId, CompanyRole.Role.OWNER, CompanyRole.Role.ADMIN)).isFalse();
    }

    @Test
    @DisplayName("getRole → retourne le role")
    void getRole() {
        CompanyRole cr = new CompanyRole();
        cr.setRole(CompanyRole.Role.MANAGER);
        when(roleRepo.findByCompanyIdAndUserId(companyId, userId)).thenReturn(Optional.of(cr));
        assertThat(checker.getRole(userId, companyId)).isEqualTo(CompanyRole.Role.MANAGER);
    }

    @Test
    @DisplayName("getRole → pas de role → null")
    void getRole_null() {
        when(roleRepo.findByCompanyIdAndUserId(companyId, userId)).thenReturn(Optional.empty());
        assertThat(checker.getRole(userId, companyId)).isNull();
    }
}
