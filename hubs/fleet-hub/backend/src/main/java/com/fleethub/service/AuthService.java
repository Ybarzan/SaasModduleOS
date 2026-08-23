package com.fleethub.service;

import com.fleethub.dto.AuthResponse;
import com.fleethub.dto.ForgotPasswordRequest;
import com.fleethub.dto.LoginRequest;
import com.fleethub.dto.RegisterRequest;
import com.fleethub.dto.ResetPasswordRequest;
import com.fleethub.model.AppUser;
import com.fleethub.model.Company;
import com.fleethub.repository.AppUserRepository;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.security.JwtService;
import com.fleethub.security.TwoFactorService;
import com.fleethub.service.email.EmailNotifier;
import com.fleethub.service.email.MailProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RESET_TTL_HOURS = 1;

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final AppUserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailNotifier emailNotifier;
    private final MailProperties mailProperties;
    private final AuditService auditService;
    private final TwoFactorService twoFactorService;

    @Value("${app.registration.trial-days}")
    private int trialDays;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        AppUser user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new UsernameNotFoundException("Utilisateur introuvable"));

        // 2FA : si activé, exiger le code TOTP
        if (user.isTotpEnabled()) {
            if (request.totpCode() == null || request.totpCode().isBlank()) {
                // Retourner un réponse indiquant que le 2FA est requis (pas de token)
                return toAuthResponsePending2FA(user);
            }
            if (!twoFactorService.verifyCode(user.getTotpSecret(), request.totpCode())) {
                throw new org.springframework.security.authentication.BadCredentialsException("Code TOTP invalide");
            }
        }

        AuthResponse response = toAuthResponse(user);
        Long companyId = user.getCompany() != null ? user.getCompany().getId() : null;
        auditService.logForUsername(companyId, user.getUsername(), "CONNEXION", "Connexion réussie", null);
        return response;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.findByUsername(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un compte existe déjà avec cet email");
        }

        Company company = new Company();
        company.setName(request.companyName().trim());
        company.setPlan(Company.SubscriptionPlan.TRIAL);
        company.setStatus(Company.CompanyStatus.TRIAL);
        company.setTrialEndsAt(LocalDateTime.now().plusDays(trialDays));
        company.setCreatedAt(LocalDateTime.now());
        company.setCountry("FR");
        companyRepository.save(company);

        AppUser owner = new AppUser();
        owner.setUsername(email);
        owner.setEmail(email);
        owner.setPassword(passwordEncoder.encode(request.password()));
        owner.setRole("ADMIN");
        owner.setDisplayName(request.firstName().trim() + " " + request.lastName().trim());
        owner.setEnabled(true);
        owner.setCreatedAt(LocalDateTime.now());
        owner.setCompany(company);
        userRepository.save(owner);

        emailNotifier.welcome(email, request.firstName().trim(), company.getName());
        auditService.logForUser(owner, "CREATION_COMPTE",
                "Création du compte et de la société " + company.getName(), null);

        return toAuthResponse(owner);
    }

    /**
     * Ne révèle jamais si le compte existe (anti-énumération) : répond toujours
     * normalement, l'email n'est envoyé que si un compte actif correspond.
     */
    @Transactional
    public void forgotPassword(ForgotPasswordRequest request) {
        String username = request.username().trim().toLowerCase();
        userRepository.findByUsername(username).ifPresent(user -> {
            if (!user.isEnabled()) {
                return;
            }
            user.setResetToken(generateToken());
            user.setResetTokenExpiresAt(LocalDateTime.now().plusHours(RESET_TTL_HOURS));
            userRepository.save(user);

            String resetUrl = mailProperties.getBaseUrl() + "/reset-password?token=" + user.getResetToken();
            emailNotifier.passwordReset(user.getEmail(), resetUrl, user.getResetTokenExpiresAt());
            auditService.logForUser(user, "DEMANDE_REINITIALISATION_MDP",
                    "Demande de réinitialisation du mot de passe", null);
        });
    }

    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        AppUser user = userRepository.findByResetToken(request.token())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Ce lien de réinitialisation est invalide ou a déjà été utilisé"));
        if (user.getResetTokenExpiresAt() == null || user.getResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ce lien de réinitialisation a expiré");
        }
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setResetToken(null);
        user.setResetTokenExpiresAt(null);
        userRepository.save(user);
        auditService.logForUser(user, "REINITIALISATION_MDP", "Mot de passe réinitialisé", null);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private AuthResponse toAuthResponse(AppUser user) {
        Long companyId = user.getCompany() != null ? user.getCompany().getId() : null;
        if (user.getCompany() != null && !user.getCompany().canLogin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Votre compte a été résilié. Contactez le support.");
        }
        String token = jwtService.generateToken(user.getUsername(), user.getRole(), companyId);
        return new AuthResponse(
                token,
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                user.getEmail(),
                companyId,
                user.getCompany() != null ? user.getCompany().getName() : null,
                user.getCompany() != null ? user.getCompany().getPlan().name() : null,
                user.getCompany() != null ? user.getCompany().getStatus().name() : null,
                user.getCompany() == null || user.getCompany().hasActiveSubscription(),
                false,
                user.isTotpEnabled());
    }

    /**
     * Réponse sans token : indique au frontend que le 2FA est requis.
     * Le client ré-enchaine avec le même login + code TOTP.
     */
    private AuthResponse toAuthResponsePending2FA(AppUser user) {
        return new AuthResponse(
                null,
                user.getUsername(),
                user.getDisplayName(),
                user.getRole(),
                user.getEmail(),
                null, null, null, null, false,
                true,
                user.isTotpEnabled());
    }
}
