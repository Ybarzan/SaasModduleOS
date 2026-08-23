package com.fleethub.service;

import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.dto.AcceptInvitationRequest;
import com.fleethub.dto.InviteUserRequest;
import com.fleethub.dto.InviteUserResponse;
import com.fleethub.dto.UpdateUserRequest;
import com.fleethub.dto.UserDto;
import com.fleethub.model.AppUser;
import com.fleethub.repository.AppUserRepository;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.service.email.EmailNotifier;
import com.fleethub.service.email.MailProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

/**
 * Gestion des utilisateurs d'un tenant (ADMIN uniquement) : invitation par
 * email, acceptation de l'invitation (création du mot de passe), mise à jour
 * du rôle / activation-désactivation, et suppression. Garantit qu'il reste
 * toujours au moins un ADMIN actif dans la société.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int INVITE_TTL_HOURS = 72;

    private final AppUserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailNotifier emailNotifier;
    private final MailProperties mailProperties;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public List<UserDto> list(Long companyId) {
        return userRepository.findByCompanyId(companyId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public InviteUserResponse invite(Long companyId, InviteUserRequest request) {
        String username = request.email().trim().toLowerCase();
        if (userRepository.findByUsername(username).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un compte existe déjà avec cet email");
        }
        String role = request.role() == null ? "GESTIONNAIRE" : request.role();

        AppUser user = new AppUser();
        user.setUsername(username);
        user.setEmail(username);
        user.setDisplayName(request.firstName().trim() + " " + request.lastName().trim());
        user.setRole(role);
        user.setPassword(passwordEncoder.encode(randomPassword()));
        user.setEnabled(false);
        user.setCreatedAt(LocalDateTime.now());
        user.setInviteToken(generateToken());
        user.setInviteTokenExpiresAt(LocalDateTime.now().plusHours(INVITE_TTL_HOURS));
        user.setCompany(companyRepository.getReferenceById(companyId));
        userRepository.save(user);

        String inviteUrl = mailProperties.getBaseUrl() + "/accept-invitation?token=" + user.getInviteToken();
        emailNotifier.invitation(user.getEmail(), "Fleet Hub", inviteUrl, user.getInviteTokenExpiresAt());
        auditService.log("INVITATION_UTILISATEUR", "Invitation de " + user.getEmail() + " (rôle " + role + ")");
        return new InviteUserResponse(toDto(user), inviteUrl);
    }

    @Transactional
    public void acceptInvitation(AcceptInvitationRequest request) {
        AppUser user = userRepository.findByInviteToken(request.token())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Ce lien d'invitation est invalide ou a déjà été utilisé"));
        if (user.getInviteTokenExpiresAt() == null
                || user.getInviteTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce lien d'invitation a expiré");
        }
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setEnabled(true);
        user.setInviteToken(null);
        user.setInviteTokenExpiresAt(null);
        userRepository.save(user);
        auditService.log("ACCEPTATION_INVITATION", "Activation du compte " + user.getEmail());
    }

    @Transactional
    public UserDto update(Long companyId, Long userId, UpdateUserRequest request) {
        AppUser user = requireUser(companyId, userId);
        if (request.role() != null && !request.role().isBlank() && !request.role().equals(user.getRole())) {
            ensureValidRole(request.role());
            ensureNotLastAdmin(companyId, user, "changer le rôle du dernier ADMIN actif");
            user.setRole(request.role());
        }
        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.enabled() != null && !request.enabled().equals(user.isEnabled())) {
            if (user.getRole().equals("ADMIN") && user.isEnabled() && !request.enabled()
                    && userRepository.countByCompanyIdAndRoleAndEnabled(companyId, "ADMIN", true) <= 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Impossible de désactiver le dernier ADMIN actif de la société");
            }
            user.setEnabled(request.enabled());
        }
        auditService.log("MODIFICATION_UTILISATEUR", "Mise à jour de " + user.getUsername());
        return toDto(userRepository.save(user));
    }

    @Transactional
    public void delete(Long companyId, Long userId) {
        AppUser user = requireUser(companyId, userId);
        if (user.getRole().equals("ADMIN") && user.isEnabled()
                && userRepository.countByCompanyIdAndRoleAndEnabled(companyId, "ADMIN", true) <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Impossible de supprimer le dernier ADMIN actif de la société");
        }
        auditService.log("SUPPRESSION_UTILISATEUR", "Suppression de l'utilisateur " + user.getUsername());
        userRepository.delete(user);
    }

    private AppUser requireUser(Long companyId, Long userId) {
        return userRepository.findByIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
    }

    private void ensureValidRole(String role) {
        if (!role.equals("ADMIN") && !role.equals("GESTIONNAIRE")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rôle invalide");
        }
    }

    private void ensureNotLastAdmin(Long companyId, AppUser user, String reason) {
        if (user.getRole().equals("ADMIN") && user.isEnabled()
                && userRepository.countByCompanyIdAndRoleAndEnabled(companyId, "ADMIN", true) <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible de " + reason);
        }
    }

    private String randomPassword() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private UserDto toDto(AppUser u) {
        return new UserDto(u.getId(), u.getUsername(), u.getEmail(), u.getDisplayName(),
                u.getRole(), u.isEnabled(), u.getCreatedAt());
    }
}
