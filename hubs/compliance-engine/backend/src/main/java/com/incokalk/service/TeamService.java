package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.User;
import com.incokalk.model.CustomRole;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.CompanyRoleRepository;
import com.incokalk.repository.CustomRoleRepository;
import com.incokalk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TeamService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepo;
    private final CompanyRepository companyRepo;
    private final CompanyRoleRepository companyRoleRepo;
    private final CustomRoleRepository customRoleRepo;
    private final BCryptPasswordEncoder encoder;
    private final EmailService emailService;

    @Transactional(readOnly = true)
    public List<User> listMembers(UUID companyId) {
        return userRepo.findByCompanyIdOrderByCreatedAtAsc(companyId);
    }

    @Transactional
    public User inviteMember(String email, String fullName, CompanyRole.Role role, UUID companyId, UUID actingUserId) {
        if (userRepo.existsByEmail(email.toLowerCase())) {
            throw new IllegalArgumentException("Cet email est déjà utilisé");
        }

        assertCanAssignRole(companyId, actingUserId, role);

        String tempPassword = generateTempPassword();
        Company company = companyRepo.getReferenceById(companyId);

        User user = User.builder()
            .email(email.toLowerCase())
            .password(encoder.encode(tempPassword))
            .fullName(fullName)
            .company(company)
            .plan(User.Plan.FREE)
            .active(true)
            .build();

        userRepo.save(user);

        companyRoleRepo.save(CompanyRole.builder()
            .company(company)
            .user(user)
            .role(role)
            .build());

        log.info("Membre invité: {} dans company={}", email, companyId);
        emailService.sendTeamInvitation(email, tempPassword, company.getName());

        return user;
    }

    @Transactional
    public CompanyRole updateMemberRole(UUID userId, CompanyRole.Role newRole, UUID companyId, UUID actingUserId) {
        CompanyRole companyRole = companyRoleRepo.findByCompanyIdAndUserId(companyId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Membre non trouvé"));

        assertCanAssignRole(companyId, actingUserId, newRole);
        assertNotDemotingLastOwner(companyId, companyRole.getRole(), newRole);

        companyRole.setRole(newRole);
        return companyRoleRepo.save(companyRole);
    }

    @Transactional
    public void removeMember(UUID userId, UUID companyId) {
        CompanyRole companyRole = companyRoleRepo.findByCompanyIdAndUserId(companyId, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Membre non trouvé"));

        if (companyRole.getRole() == CompanyRole.Role.OWNER) {
            throw new IllegalStateException("Impossible de supprimer le propriétaire de la société");
        }

        companyRoleRepo.delete(companyRole);

        User user = userRepo.findById(userId).orElse(null);
        if (user != null) {
            user.setCompany(null);
            userRepo.save(user);
        }
    }

    @Transactional
    public User updateMember(UUID userId, String fullName, CompanyRole.Role role, UUID companyId, UUID actingUserId) {
        return updateMember(userId, fullName, role, null, companyId, actingUserId);
    }

    @Transactional
    public User updateMember(UUID userId, String fullName, CompanyRole.Role role, String customRoleId, UUID companyId, UUID actingUserId) {
        User user = userRepo.findById(userId)
            .filter(u -> u.getCompany() != null && u.getCompany().getId().equals(companyId))
            .orElseThrow(() -> new ResourceNotFoundException("Membre non trouvé"));

        if (fullName != null) user.setFullName(fullName);
        userRepo.save(user);

        if (role != null || customRoleId != null) {
            CompanyRole companyRole = companyRoleRepo.findByCompanyIdAndUserId(companyId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Rôle non trouvé"));
            if (role != null) {
                assertCanAssignRole(companyId, actingUserId, role);
                assertNotDemotingLastOwner(companyId, companyRole.getRole(), role);
                companyRole.setRole(role);
            }
            if (customRoleId != null) {
                if (customRoleId.isBlank()) {
                    companyRole.setCustomRole(null);
                } else {
                    CustomRole customRole = customRoleRepo.findByIdAndCompanyId(UUID.fromString(customRoleId), companyId)
                        .orElseThrow(() -> new ResourceNotFoundException("Rôle personnalisé non trouvé"));
                    companyRole.setCustomRole(customRole);
                }
            }
            companyRoleRepo.save(companyRole);
        }

        return user;
    }

    /**
     * Empêche l'escalade de privilèges : un utilisateur ne peut jamais attribuer
     * (par invitation ou changement de rôle) un rôle plus élevé que le sien.
     * {@link CompanyRole.Role} est déclaré du plus élevé au moins élevé (OWNER en premier),
     * donc un ordinal plus petit signifie un rôle plus privilégié.
     */
    private void assertCanAssignRole(UUID companyId, UUID actingUserId, CompanyRole.Role targetRole) {
        CompanyRole.Role actingRole = companyRoleRepo.findByCompanyIdAndUserId(companyId, actingUserId)
            .map(CompanyRole::getRole)
            .orElseThrow(() -> new SecurityException("Rôle de l'utilisateur courant introuvable dans cette société"));

        if (targetRole.ordinal() < actingRole.ordinal()) {
            throw new SecurityException("Vous ne pouvez pas attribuer un rôle supérieur au vôtre");
        }
    }

    /**
     * Empêche de rétrograder le dernier OWNER restant d'une société, ce qui la
     * laisserait sans aucun propriétaire.
     */
    private void assertNotDemotingLastOwner(UUID companyId, CompanyRole.Role currentRole, CompanyRole.Role newRole) {
        if (currentRole == CompanyRole.Role.OWNER && newRole != CompanyRole.Role.OWNER) {
            long ownerCount = companyRoleRepo.countByCompanyIdAndRole(companyId, CompanyRole.Role.OWNER);
            if (ownerCount <= 1) {
                throw new IllegalStateException("Impossible de rétrograder le dernier propriétaire de la société");
            }
        }
    }

    public Optional<CompanyRole> findUserRole(UUID companyId, UUID userId) {
        return companyRoleRepo.findByCompanyIdAndUserId(companyId, userId);
    }

    public List<CompanyRole> findRoles(UUID companyId) {
        return companyRoleRepo.findByCompanyId(companyId);
    }

    private String generateTempPassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            sb.append(chars.charAt(SECURE_RANDOM.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
