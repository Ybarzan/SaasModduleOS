package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.User;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.CompanyRoleRepository;
import com.incokalk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("TeamService — Tests unitaires")
class TeamServiceTest {

    @Mock UserRepository userRepo;
    @Mock CompanyRepository companyRepo;
    @Mock CompanyRoleRepository companyRoleRepo;
    @Mock BCryptPasswordEncoder encoder;
    @Mock EmailService emailService;

    @InjectMocks TeamService service;

    private UUID companyId;
    private UUID userId;
    private UUID actingUserId;
    private Company company;
    private User user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        userId = UUID.randomUUID();
        actingUserId = UUID.randomUUID();

        company = Company.builder().id(companyId).name("TestCo").slug("testco").build();
        user = User.builder()
                .id(userId)
                .email("membre@test.com")
                .password("encodedPwd")
                .fullName("Membre Test")
                .plan(User.Plan.FREE)
                .active(true)
                .company(company)
                .build();
    }

    private void actingUserHasRole(CompanyRole.Role role) {
        CompanyRole actingCompanyRole = CompanyRole.builder()
                .id(UUID.randomUUID()).company(company).role(role).build();
        when(companyRoleRepo.findByCompanyIdAndUserId(companyId, actingUserId))
                .thenReturn(Optional.of(actingCompanyRole));
    }

    // ── inviteMember ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Invitation membre réussie")
    void inviteMember_success() {
        when(userRepo.existsByEmail("new@test.com")).thenReturn(false);
        when(companyRepo.getReferenceById(companyId)).thenReturn(company);
        when(encoder.encode(anyString())).thenReturn("encodedTmp");
        when(userRepo.save(any(User.class))).thenAnswer(i -> {
            User u = i.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });
        when(companyRoleRepo.save(any(CompanyRole.class))).thenAnswer(i -> i.getArgument(0));
        actingUserHasRole(CompanyRole.Role.MANAGER);

        User result = service.inviteMember("new@test.com", "Nouveau Membre", CompanyRole.Role.USER, companyId, actingUserId);

        assertThat(result.getEmail()).isEqualTo("new@test.com");
        assertThat(result.getFullName()).isEqualTo("Nouveau Membre");
        assertThat(result.getCompany()).isEqualTo(company);
        assertThat(result.isActive()).isTrue();
        verify(encoder).encode(anyString());
        verify(companyRoleRepo).save(any(CompanyRole.class));
    }

    @Test
    @DisplayName("Invitation avec email dupliqué → exception")
    void inviteMember_duplicateEmail_throws() {
        when(userRepo.existsByEmail("dup@test.com")).thenReturn(true);

        assertThatThrownBy(() -> service.inviteMember("dup@test.com", "Dup", CompanyRole.Role.USER, companyId, actingUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("déjà utilisé");

        verify(userRepo, never()).save(any());
    }

    @Test
    @DisplayName("Email normalisé en minuscules lors de l'invitation")
    void inviteMember_emailNormalized() {
        when(userRepo.existsByEmail("upper@test.com")).thenReturn(false);
        when(companyRepo.getReferenceById(companyId)).thenReturn(company);
        when(encoder.encode(anyString())).thenReturn("encoded");
        when(userRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(companyRoleRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        actingUserHasRole(CompanyRole.Role.MANAGER);

        User result = service.inviteMember("UPPER@Test.com", "Test", CompanyRole.Role.USER, companyId, actingUserId);

        assertThat(result.getEmail()).isEqualTo("upper@test.com");
    }

    // ── updateMemberRole ─────────────────────────────────────────────────

    @Test
    @DisplayName("Mise à jour rôle réussie")
    void updateMemberRole_success() {
        CompanyRole existingRole = CompanyRole.builder()
            .id(UUID.randomUUID()).company(company).user(user).role(CompanyRole.Role.USER).build();
        when(companyRoleRepo.findByCompanyIdAndUserId(companyId, userId)).thenReturn(Optional.of(existingRole));
        when(companyRoleRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        actingUserHasRole(CompanyRole.Role.OWNER);

        CompanyRole result = service.updateMemberRole(userId, CompanyRole.Role.MANAGER, companyId, actingUserId);

        assertThat(result.getRole()).isEqualTo(CompanyRole.Role.MANAGER);
    }

    @Test
    @DisplayName("Mise à jour rôle membre introuvable → exception")
    void updateMemberRole_notFound_throws() {
        when(companyRoleRepo.findByCompanyIdAndUserId(companyId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateMemberRole(userId, CompanyRole.Role.USER, companyId, actingUserId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Mise à jour rôle d'un membre d'une autre entreprise → exception")
    void updateMemberRole_wrongCompany_throws() {
        UUID otherCompanyId = UUID.randomUUID();
        when(companyRoleRepo.findByCompanyIdAndUserId(otherCompanyId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateMemberRole(userId, CompanyRole.Role.USER, otherCompanyId, actingUserId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("MANAGER tente de se promouvoir OWNER → rejeté (403)")
    void updateMemberRole_managerPromotesSelfToOwner_throwsSecurityException() {
        CompanyRole existingRole = CompanyRole.builder()
            .id(UUID.randomUUID()).company(company).user(user).role(CompanyRole.Role.MANAGER).build();
        when(companyRoleRepo.findByCompanyIdAndUserId(companyId, userId)).thenReturn(Optional.of(existingRole));
        actingUserHasRole(CompanyRole.Role.MANAGER);

        assertThatThrownBy(() -> service.updateMemberRole(userId, CompanyRole.Role.OWNER, companyId, actingUserId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("supérieur");

        verify(companyRoleRepo, never()).save(any());
    }

    @Test
    @DisplayName("ADMIN tente de promouvoir un membre OWNER → rejeté (403)")
    void updateMemberRole_adminPromotesOtherToOwner_throwsSecurityException() {
        CompanyRole existingRole = CompanyRole.builder()
            .id(UUID.randomUUID()).company(company).user(user).role(CompanyRole.Role.USER).build();
        when(companyRoleRepo.findByCompanyIdAndUserId(companyId, userId)).thenReturn(Optional.of(existingRole));
        actingUserHasRole(CompanyRole.Role.ADMIN);

        assertThatThrownBy(() -> service.updateMemberRole(userId, CompanyRole.Role.OWNER, companyId, actingUserId))
                .isInstanceOf(SecurityException.class);

        verify(companyRoleRepo, never()).save(any());
    }

    @Test
    @DisplayName("Rétrograder le dernier OWNER restant → rejeté (409)")
    void updateMemberRole_demoteLastOwner_throwsIllegalState() {
        CompanyRole ownerRole = CompanyRole.builder()
            .id(UUID.randomUUID()).company(company).user(user).role(CompanyRole.Role.OWNER).build();
        when(companyRoleRepo.findByCompanyIdAndUserId(companyId, userId)).thenReturn(Optional.of(ownerRole));
        when(companyRoleRepo.countByCompanyIdAndRole(companyId, CompanyRole.Role.OWNER)).thenReturn(1L);
        actingUserHasRole(CompanyRole.Role.OWNER);

        assertThatThrownBy(() -> service.updateMemberRole(userId, CompanyRole.Role.ADMIN, companyId, actingUserId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dernier propriétaire");

        verify(companyRoleRepo, never()).save(any());
    }

    @Test
    @DisplayName("Rétrograder un OWNER quand il en reste un autre → autorisé")
    void updateMemberRole_demoteOwner_whenAnotherOwnerRemains_succeeds() {
        CompanyRole ownerRole = CompanyRole.builder()
            .id(UUID.randomUUID()).company(company).user(user).role(CompanyRole.Role.OWNER).build();
        when(companyRoleRepo.findByCompanyIdAndUserId(companyId, userId)).thenReturn(Optional.of(ownerRole));
        when(companyRoleRepo.countByCompanyIdAndRole(companyId, CompanyRole.Role.OWNER)).thenReturn(2L);
        when(companyRoleRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        actingUserHasRole(CompanyRole.Role.OWNER);

        CompanyRole result = service.updateMemberRole(userId, CompanyRole.Role.ADMIN, companyId, actingUserId);

        assertThat(result.getRole()).isEqualTo(CompanyRole.Role.ADMIN);
    }

    // ── removeMember ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Suppression membre réussie (non-owner)")
    void removeMember_success() {
        CompanyRole memberRole = CompanyRole.builder()
            .id(UUID.randomUUID()).company(company).user(user).role(CompanyRole.Role.USER).build();
        when(companyRoleRepo.findByCompanyIdAndUserId(companyId, userId)).thenReturn(Optional.of(memberRole));
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        service.removeMember(userId, companyId);

        verify(companyRoleRepo).delete(memberRole);
        assertThat(user.getCompany()).isNull();
    }

    @Test
    @DisplayName("Suppression owner → exception")
    void removeMember_owner_throws() {
        CompanyRole ownerRole = CompanyRole.builder()
            .id(UUID.randomUUID()).company(company).user(user).role(CompanyRole.Role.OWNER).build();
        when(companyRoleRepo.findByCompanyIdAndUserId(companyId, userId)).thenReturn(Optional.of(ownerRole));

        assertThatThrownBy(() -> service.removeMember(userId, companyId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("propriétaire");

        verify(companyRoleRepo, never()).delete(any());
    }

    @Test
    @DisplayName("Suppression membre introuvable → exception")
    void removeMember_notFound_throws() {
        when(companyRoleRepo.findByCompanyIdAndUserId(companyId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeMember(userId, companyId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── updateMember ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Mise à jour partielle du membre (fullName et role)")
    void updateMember_success() {
        CompanyRole existingRole = CompanyRole.builder()
            .id(UUID.randomUUID()).company(company).user(user).role(CompanyRole.Role.USER).build();
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(userRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(companyRoleRepo.findByCompanyIdAndUserId(companyId, userId)).thenReturn(Optional.of(existingRole));
        when(companyRoleRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        actingUserHasRole(CompanyRole.Role.ADMIN);

        service.updateMember(userId, "Nouveau Nom", CompanyRole.Role.MANAGER, companyId, actingUserId);

        assertThat(user.getFullName()).isEqualTo("Nouveau Nom");
        assertThat(existingRole.getRole()).isEqualTo(CompanyRole.Role.MANAGER);
    }

    @Test
    @DisplayName("Mise à jour partielle avec null ne modifie pas le champ")
    void updateMember_partialUpdate_preservesFields() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(userRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.updateMember(userId, null, null, companyId, actingUserId);

        assertThat(user.getFullName()).isEqualTo("Membre Test");
    }

    @Test
    @DisplayName("Mise à jour membre introuvable → exception")
    void updateMember_notFound_throws() {
        when(userRepo.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateMember(userId, "Nom", CompanyRole.Role.USER, companyId, actingUserId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Mise à jour membre d'une autre entreprise → exception")
    void updateMember_wrongCompany_throws() {
        UUID otherCompanyId = UUID.randomUUID();
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.updateMember(userId, "Nom", CompanyRole.Role.USER, otherCompanyId, actingUserId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("ADMIN tente de se promouvoir OWNER via updateMember → rejeté (403)")
    void updateMember_adminPromotesSelfToOwner_throwsSecurityException() {
        CompanyRole existingRole = CompanyRole.builder()
            .id(UUID.randomUUID()).company(company).user(user).role(CompanyRole.Role.ADMIN).build();
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(userRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        // L'utilisateur agissant EST le membre ciblé (auto-promotion)
        when(companyRoleRepo.findByCompanyIdAndUserId(companyId, userId)).thenReturn(Optional.of(existingRole));

        assertThatThrownBy(() -> service.updateMember(userId, null, CompanyRole.Role.OWNER, companyId, userId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("supérieur");

        verify(companyRoleRepo, never()).save(any());
    }

    @Test
    @DisplayName("MANAGER tente d'attribuer le rôle ADMIN à un membre → rejeté (403)")
    void updateMember_managerAssignsHigherRole_throwsSecurityException() {
        CompanyRole existingRole = CompanyRole.builder()
            .id(UUID.randomUUID()).company(company).user(user).role(CompanyRole.Role.USER).build();
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(userRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(companyRoleRepo.findByCompanyIdAndUserId(companyId, userId)).thenReturn(Optional.of(existingRole));
        actingUserHasRole(CompanyRole.Role.MANAGER);

        assertThatThrownBy(() -> service.updateMember(userId, null, CompanyRole.Role.ADMIN, companyId, actingUserId))
                .isInstanceOf(SecurityException.class);

        verify(companyRoleRepo, never()).save(any());
    }

    @Test
    @DisplayName("Rétrograder le dernier OWNER via updateMember → rejeté (409)")
    void updateMember_demoteLastOwner_throwsIllegalState() {
        CompanyRole ownerRole = CompanyRole.builder()
            .id(UUID.randomUUID()).company(company).user(user).role(CompanyRole.Role.OWNER).build();
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(userRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(companyRoleRepo.findByCompanyIdAndUserId(companyId, userId)).thenReturn(Optional.of(ownerRole));
        when(companyRoleRepo.countByCompanyIdAndRole(companyId, CompanyRole.Role.OWNER)).thenReturn(1L);
        actingUserHasRole(CompanyRole.Role.OWNER);

        assertThatThrownBy(() -> service.updateMember(userId, null, CompanyRole.Role.ADMIN, companyId, actingUserId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("dernier propriétaire");

        verify(companyRoleRepo, never()).save(any());
    }

    // ── listMembers ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Liste des membres filtrée par entreprise")
    void listMembers_filteredByCompany() {
        User m2 = User.builder().id(UUID.randomUUID()).email("m2@test.com").company(company).build();
        when(userRepo.findByCompanyIdOrderByCreatedAtAsc(companyId)).thenReturn(List.of(user, m2));

        List<User> result = service.listMembers(companyId);

        assertThat(result).hasSize(2);
    }
}
