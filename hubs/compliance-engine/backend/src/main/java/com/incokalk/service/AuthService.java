package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.RefreshToken;
import com.incokalk.model.User;
import com.incokalk.repository.CompanyRoleRepository;
import com.incokalk.repository.RefreshTokenRepository;
import com.incokalk.repository.UserRepository;
import com.incokalk.repository.SimulationRepository;
import com.incokalk.repository.ApiKeyRepository;
import com.incokalk.security.JwtService;
import com.incokalk.security.LoginRateLimiter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepo;
    private final SimulationRepository simRepo;
    private final ApiKeyRepository keyRepo;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder encoder;
    private final CompanyService companyService;
    private final CompanyRoleRepository companyRoleRepo;
    private final RefreshTokenRepository refreshTokenRepo;
    private final LoginRateLimiter loginRateLimiter;
    private final EmailService emailService;

    @Transactional
    public AuthResult register(String email, String password, String fullName, String companyName) {
        return register(email, password, fullName, companyName, null);
    }

    @Transactional
    public AuthResult register(String email, String password, String fullName, String companyName, String referralCode) {
        if (userRepo.existsByEmail(email.toLowerCase()))
            throw new IllegalArgumentException("Email déjà utilisé");

        String verificationToken = UUID.randomUUID().toString();

        User user = User.builder()
            .email(email.toLowerCase())
            .password(encoder.encode(password))
            .fullName(fullName)
            .plan(User.Plan.FREE)
            .verificationToken(verificationToken)
            .emailVerified(false)
            .build();
        userRepo.save(user);
        log.info("Nouvel utilisateur créé: {}", email);

        String effectiveCompanyName = (companyName != null && !companyName.isBlank())
            ? companyName
            : "Entreprise de " + fullName;
        String slug = companyService.generateUniqueSlug(effectiveCompanyName);
        Company company = companyService.createCompany(effectiveCompanyName, slug, user.getId());

        // Code de parrainage invalide/inconnu -> ignoré silencieusement plutôt que de
        // faire échouer toute l'inscription pour une faute de frappe sur un champ
        // optionnel et non critique.
        if (referralCode != null && !referralCode.isBlank()) {
            companyService.findByReferralCode(referralCode.trim().toUpperCase()).ifPresentOrElse(
                referrer -> {
                    companyService.setReferredBy(company.getId(), referrer.getId());
                    log.info("Société {} parrainée par {} (code {})", company.getId(), referrer.getId(), referralCode);
                },
                () -> log.info("Code de parrainage '{}' invalide, ignoré", referralCode)
            );
        }

        user.setCompany(company);
        userRepo.save(user);
        String role = "OWNER";
        log.info("Company '{}' créée pour l'utilisateur {}", effectiveCompanyName, email);

        String token = jwtService.generateToken(user.getId(), email, "FREE", role);
        String refreshToken = jwtService.generateRefreshToken(user.getId());
        refreshTokenRepo.save(RefreshToken.builder()
            .user(user)
            .token(refreshToken)
            .expiresAt(LocalDateTime.now().plusDays(30))
            .build());

        emailService.sendWelcomeEmail(email, fullName);

        return new AuthResult(token, refreshToken, user.getId(), email, "FREE", role, fullName);
    }

    @Transactional
    public AuthResult login(String email, String password) {
        if (loginRateLimiter.isBlocked(email)) {
            throw new IllegalStateException("Trop de tentatives de connexion. Réessayez dans 15 minutes.");
        }

        User user = userRepo.findByEmail(email.toLowerCase())
            .orElseThrow(() -> new IllegalArgumentException("Email ou mot de passe incorrect"));

        if (!user.isActive()) throw new IllegalStateException("Compte désactivé");
        if (!encoder.matches(password, user.getPassword()))
            throw new IllegalArgumentException("Email ou mot de passe incorrect");

        loginRateLimiter.reset(email);

        String role = "USER";
        if (user.getCompany() != null) {
            role = companyRoleRepo.findByCompanyIdAndUserId(user.getCompany().getId(), user.getId())
                .map(r -> r.getRole().name())
                .orElse("USER");
        }
        String fullName = user.getFullName() != null ? user.getFullName() : "";
        log.info("Connexion : {} plan={} role={}", email, user.getPlan(), role);

        String token = jwtService.generateToken(user.getId(), email, user.getPlan().name(), role);
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        refreshTokenRepo.revokeAllByUserId(user.getId());
        refreshTokenRepo.save(RefreshToken.builder()
            .user(user)
            .token(refreshToken)
            .expiresAt(LocalDateTime.now().plusDays(30))
            .build());

        return new AuthResult(token, refreshToken, user.getId(), email, user.getPlan().name(), role, fullName);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getProfile(UUID userId) {
        User u = userRepo.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));
        Map<String, Object> profile = new java.util.LinkedHashMap<>();
        profile.put("id", u.getId());
        profile.put("email", u.getEmail());
        profile.put("full_name", u.getFullName() != null ? u.getFullName() : "");
        profile.put("company", u.getCompany() != null ? u.getCompany().getName() : "");
        profile.put("plan", u.getPlan().name());
        String role = "USER";
        if (u.getCompany() != null) {
            role = companyRoleRepo.findByCompanyIdAndUserId(u.getCompany().getId(), u.getId())
                .map(r -> r.getRole().name())
                .orElse("USER");
        }
        profile.put("role", role);
        profile.put("company_id", u.getCompany() != null ? u.getCompany().getId() : null);
        profile.put("email_verified", u.isEmailVerified());
        profile.put("created_at", u.getCreatedAt());
        profile.put("total_simulations", simRepo.countByUserId(userId));
        profile.put("active_keys", keyRepo.findByUserIdAndActiveTrue(userId).size());
        return profile;
    }

    @Transactional
    public void updateProfile(UUID userId, String fullName) {
        User u = userRepo.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("Utilisateur introuvable"));
        u.setFullName(fullName);
        userRepo.save(u);
    }

    @Transactional
    public void logout(UUID userId) {
        refreshTokenRepo.revokeAllByUserId(userId);
    }

    @Transactional
    public String forgotPassword(String email) {
        User user = userRepo.findByEmail(email.toLowerCase()).orElse(null);
        if (user == null) {
            log.warn("Forgot password demandé pour email inconnu: {}", email);
            return "Si cet email existe, un lien de réinitialisation a été envoyé.";
        }

        String resetToken = UUID.randomUUID().toString();
        user.setPasswordResetToken(resetToken);
        user.setPasswordResetExpires(LocalDateTime.now().plusHours(1));
        userRepo.save(user);

        log.info("Password reset demandé pour: {}", email);
        emailService.sendPasswordReset(email, resetToken);
        return "Si cet email existe, un lien de réinitialisation a été envoyé.";
    }

    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = userRepo.findByPasswordResetToken(token)
            .orElseThrow(() -> new IllegalArgumentException("Lien de réinitialisation invalide"));

        if (user.getPasswordResetExpires() == null || user.getPasswordResetExpires().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Lien de réinitialisation expiré");
        }

        user.setPassword(encoder.encode(newPassword));
        user.setPasswordResetToken(null);
        user.setPasswordResetExpires(null);
        userRepo.save(user);
        log.info("Mot de passe réinitialisé pour {}", user.getEmail());
    }

    @Transactional
    public String verifyEmail(String token) {
        User user = userRepo.findByVerificationToken(token)
            .orElseThrow(() -> new IllegalArgumentException("Lien de vérification invalide"));

        user.setEmailVerified(true);
        user.setVerificationToken(null);
        userRepo.save(user);
        log.info("Email vérifié pour {}", user.getEmail());
        return "Email vérifié avec succès";
    }

    @Transactional
    public AuthResult refreshAccessToken(String refreshTokenValue) {
        RefreshToken refreshToken = refreshTokenRepo.findByTokenAndRevokedFalse(refreshTokenValue)
            .orElseThrow(() -> new IllegalArgumentException("Refresh token invalide"));

        if (!refreshToken.isValid()) {
            throw new IllegalArgumentException("Refresh token expiré ou révoqué");
        }

        User user = refreshToken.getUser();
        if (!user.isActive()) {
            throw new IllegalStateException("Compte désactivé");
        }

        String role = "USER";
        if (user.getCompany() != null) {
            role = companyRoleRepo.findByCompanyIdAndUserId(user.getCompany().getId(), user.getId())
                .map(r -> r.getRole().name())
                .orElse("USER");
        }

        String newAccessToken = jwtService.generateToken(user.getId(), user.getEmail(), user.getPlan().name(), role);
        String newRefreshToken = jwtService.generateRefreshToken(user.getId());

        refreshToken.setRevoked(true);
        refreshTokenRepo.save(refreshToken);

        refreshTokenRepo.save(RefreshToken.builder()
            .user(user)
            .token(newRefreshToken)
            .expiresAt(LocalDateTime.now().plusDays(30))
            .build());

        log.info("Token rafraîchi pour {}", user.getEmail());
        return new AuthResult(newAccessToken, newRefreshToken, user.getId(), user.getEmail(), user.getPlan().name(), role, user.getFullName());
    }

    public record AuthResult(String token, String refreshToken, UUID userId, String email, String plan, String role, String fullName) {}
}
