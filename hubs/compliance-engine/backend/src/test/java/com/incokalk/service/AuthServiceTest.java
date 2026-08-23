package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.RefreshToken;
import com.incokalk.model.User;
import com.incokalk.repository.ApiKeyRepository;
import com.incokalk.repository.CompanyRoleRepository;
import com.incokalk.repository.RefreshTokenRepository;
import com.incokalk.repository.SimulationRepository;
import com.incokalk.repository.UserRepository;
import com.incokalk.security.JwtService;
import com.incokalk.security.LoginRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("AuthService — Tests unitaires")
class AuthServiceTest {

    @Mock UserRepository userRepo;
    @Mock SimulationRepository simRepo;
    @Mock ApiKeyRepository keyRepo;
    @Mock JwtService jwtService;
    @Mock BCryptPasswordEncoder encoder;
    @Mock CompanyService companyService;
    @Mock CompanyRoleRepository companyRoleRepo;
    @Mock RefreshTokenRepository refreshTokenRepo;
    @Mock LoginRateLimiter loginRateLimiter;
    @Mock EmailService emailService;

    @InjectMocks AuthService service;

    private UUID userId;
    private UUID companyId;
    private Company company;
    private User user;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userId = UUID.randomUUID();
        companyId = UUID.randomUUID();

        company = Company.builder().id(companyId).name("TestCo").slug("testco").build();
        user = User.builder()
                .id(userId)
                .email("test@example.com")
                .password("encodedPwd")
                .fullName("Test User")
                .plan(User.Plan.FREE)
                .active(true)
                .emailVerified(false)
                .company(company)
                .build();
    }

    // ── register ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Inscription réussie sans nom d'entreprise fourni — crée une entreprise auto-nommée")
    void register_success_noCompany() {
        when(userRepo.existsByEmail("test@example.com")).thenReturn(false);
        when(encoder.encode("secret")).thenReturn("encodedPwd");
        when(companyService.generateUniqueSlug("Entreprise de Test User")).thenReturn("entreprise-de-test-user");
        when(companyService.createCompany(eq("Entreprise de Test User"), eq("entreprise-de-test-user"), any())).thenReturn(company);
        when(jwtService.generateToken(any(), eq("test@example.com"), eq("FREE"), eq("OWNER"))).thenReturn("jwt-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");

        var result = service.register("test@example.com", "secret", "Test User", null);

        assertThat(result.token()).isEqualTo("jwt-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.email()).isEqualTo("test@example.com");
        assertThat(result.plan()).isEqualTo("FREE");
        assertThat(result.role()).isEqualTo("OWNER");
        assertThat(result.fullName()).isEqualTo("Test User");
        verify(userRepo, times(2)).save(any(User.class));
        verify(companyService).createCompany(eq("Entreprise de Test User"), eq("entreprise-de-test-user"), any());
        verify(emailService).sendWelcomeEmail("test@example.com", "Test User");
    }

    @Test
    @DisplayName("Inscription réussie avec création d'entreprise")
    void register_success_withCompany() {
        when(userRepo.existsByEmail("new@example.com")).thenReturn(false);
        when(encoder.encode("pass")).thenReturn("encodedPwd");
        when(companyService.generateUniqueSlug("MaSociete")).thenReturn("masociete");
        when(companyService.createCompany(eq("MaSociete"), eq("masociete"), any())).thenReturn(company);
        when(jwtService.generateToken(any(), eq("new@example.com"), eq("FREE"), eq("OWNER"))).thenReturn("jwt-new");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-new");

        var result = service.register("new@example.com", "pass", "New User", "MaSociete");

        assertThat(result.token()).isEqualTo("jwt-new");
        assertThat(result.refreshToken()).isEqualTo("refresh-new");
        assertThat(result.role()).isEqualTo("OWNER");
        verify(companyService).createCompany(eq("MaSociete"), eq("masociete"), any());
        verify(userRepo, times(2)).save(any(User.class));
    }

    @Test
    @DisplayName("Inscription avec email existant → exception")
    void register_duplicateEmail_throws() {
        when(userRepo.existsByEmail("dup@example.com")).thenReturn(true);

        assertThatThrownBy(() -> service.register("dup@example.com", "pass", "Dup", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email déjà utilisé");

        verify(userRepo, never()).save(any());
    }

    @Test
    @DisplayName("Inscription avec code de parrainage valide → lie la société au parrain")
    void register_withValidReferralCode_linksReferrer() {
        UUID referrerId = UUID.randomUUID();
        Company referrer = Company.builder().id(referrerId).name("Parrain SAS").build();

        when(userRepo.existsByEmail("filleul@example.com")).thenReturn(false);
        when(encoder.encode("pass")).thenReturn("encodedPwd");
        when(companyService.generateUniqueSlug("Filleul Co")).thenReturn("filleul-co");
        when(companyService.createCompany(eq("Filleul Co"), eq("filleul-co"), any())).thenReturn(company);
        when(companyService.findByReferralCode("ABCD1234")).thenReturn(Optional.of(referrer));
        when(jwtService.generateToken(any(), eq("filleul@example.com"), eq("FREE"), eq("OWNER"))).thenReturn("jwt-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");

        service.register("filleul@example.com", "pass", "Filleul", "Filleul Co", "abcd1234");

        verify(companyService).setReferredBy(companyId, referrerId);
    }

    @Test
    @DisplayName("Inscription avec code de parrainage invalide → ignoré silencieusement")
    void register_withInvalidReferralCode_ignoredSilently() {
        when(userRepo.existsByEmail("filleul@example.com")).thenReturn(false);
        when(encoder.encode("pass")).thenReturn("encodedPwd");
        when(companyService.generateUniqueSlug("Filleul Co")).thenReturn("filleul-co");
        when(companyService.createCompany(eq("Filleul Co"), eq("filleul-co"), any())).thenReturn(company);
        when(companyService.findByReferralCode("BADCODE1")).thenReturn(Optional.empty());
        when(jwtService.generateToken(any(), eq("filleul@example.com"), eq("FREE"), eq("OWNER"))).thenReturn("jwt-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");

        var result = service.register("filleul@example.com", "pass", "Filleul", "Filleul Co", "badcode1");

        assertThat(result.token()).isEqualTo("jwt-token");
        verify(companyService, never()).setReferredBy(any(), any());
    }

    @Test
    @DisplayName("Inscription sans code de parrainage → aucun appel de liaison")
    void register_withoutReferralCode_noLinking() {
        when(userRepo.existsByEmail("test@example.com")).thenReturn(false);
        when(encoder.encode("secret")).thenReturn("encodedPwd");
        when(companyService.generateUniqueSlug("Entreprise de Test User")).thenReturn("entreprise-de-test-user");
        when(companyService.createCompany(eq("Entreprise de Test User"), eq("entreprise-de-test-user"), any())).thenReturn(company);
        when(jwtService.generateToken(any(), eq("test@example.com"), eq("FREE"), eq("OWNER"))).thenReturn("jwt-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");

        service.register("test@example.com", "secret", "Test User", null);

        verify(companyService, never()).findByReferralCode(any());
        verify(companyService, never()).setReferredBy(any(), any());
    }

    // ── login ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Connexion réussie avec bonnes credentials")
    void login_success() {
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(encoder.matches("secret", "encodedPwd")).thenReturn(true);
        when(loginRateLimiter.isBlocked("test@example.com")).thenReturn(false);
        when(companyRoleRepo.findByCompanyIdAndUserId(companyId, userId))
            .thenReturn(Optional.of(CompanyRole.builder().role(CompanyRole.Role.ADMIN).build()));
        when(jwtService.generateToken(userId, "test@example.com", "FREE", "ADMIN")).thenReturn("login-token");
        when(jwtService.generateRefreshToken(userId)).thenReturn("refresh-token");

        var result = service.login("test@example.com", "secret");

        assertThat(result.token()).isEqualTo("login-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.plan()).isEqualTo("FREE");
        assertThat(result.role()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("Connexion sans company → role USER par défaut")
    void login_noCompany_defaultRole() {
        user.setCompany(null);
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(encoder.matches("secret", "encodedPwd")).thenReturn(true);
        when(loginRateLimiter.isBlocked("test@example.com")).thenReturn(false);
        when(jwtService.generateToken(userId, "test@example.com", "FREE", "USER")).thenReturn("login-token");
        when(jwtService.generateRefreshToken(userId)).thenReturn("refresh-token");

        var result = service.login("test@example.com", "secret");

        assertThat(result.role()).isEqualTo("USER");
    }

    @Test
    @DisplayName("Connexion avec mauvais mot de passe → exception")
    void login_wrongPassword_throws() {
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(encoder.matches("wrong", "encodedPwd")).thenReturn(false);
        when(loginRateLimiter.isBlocked("test@example.com")).thenReturn(false);

        assertThatThrownBy(() -> service.login("test@example.com", "wrong"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email ou mot de passe incorrect");
    }

    @Test
    @DisplayName("Connexion avec email inconnu → exception")
    void login_unknownEmail_throws() {
        when(userRepo.findByEmail("unknown@example.com")).thenReturn(Optional.empty());
        when(loginRateLimiter.isBlocked("unknown@example.com")).thenReturn(false);

        assertThatThrownBy(() -> service.login("unknown@example.com", "pass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email ou mot de passe incorrect");
    }

    @Test
    @DisplayName("Connexion avec compte désactivé → exception")
    void login_inactiveAccount_throws() {
        user.setActive(false);
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(loginRateLimiter.isBlocked("test@example.com")).thenReturn(false);

        assertThatThrownBy(() -> service.login("test@example.com", "pass"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Compte désactivé");
    }

    // ── forgotPassword ───────────────────────────────────────────────────

    @Test
    @DisplayName("Mot de passe oublié avec email existant → message standard")
    void forgotPassword_existingEmail() {
        when(userRepo.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        String msg = service.forgotPassword("test@example.com");

        assertThat(msg).contains("Si cet email existe");
        verify(userRepo).save(any(User.class));
        assertThat(user.getPasswordResetToken()).isNotNull();
        assertThat(user.getPasswordResetExpires()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("Mot de passe oublié avec email inconnu → même message (pas d'énumération)")
    void forgotPassword_unknownEmail_noEnumeration() {
        when(userRepo.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        String msg = service.forgotPassword("unknown@example.com");

        assertThat(msg).contains("Si cet email existe");
        verify(userRepo, never()).save(any());
    }

    // ── resetPassword ────────────────────────────────────────────────────

    @Test
    @DisplayName("Réinitialisation mot de passe réussie")
    void resetPassword_success() {
        user.setPasswordResetToken("valid-token");
        user.setPasswordResetExpires(LocalDateTime.now().plusHours(1));
        when(userRepo.findByPasswordResetToken("valid-token")).thenReturn(Optional.of(user));
        when(encoder.encode("newPass")).thenReturn("newEncoded");

        service.resetPassword("valid-token", "newPass");

        verify(userRepo).save(user);
        assertThat(user.getPassword()).isEqualTo("newEncoded");
        assertThat(user.getPasswordResetToken()).isNull();
        assertThat(user.getPasswordResetExpires()).isNull();
    }

    @Test
    @DisplayName("Réinitialisation avec token expiré → exception")
    void resetPassword_expiredToken_throws() {
        user.setPasswordResetToken("expired-token");
        user.setPasswordResetExpires(LocalDateTime.now().minusHours(1));
        when(userRepo.findByPasswordResetToken("expired-token")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.resetPassword("expired-token", "newPass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("expiré");
    }

    @Test
    @DisplayName("Réinitialisation avec token invalide → exception")
    void resetPassword_invalidToken_throws() {
        when(userRepo.findByPasswordResetToken("bad-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resetPassword("bad-token", "newPass"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalide");
    }

    // ── verifyEmail ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Vérification email réussie")
    void verifyEmail_success() {
        user.setVerificationToken("verify-me");
        when(userRepo.findByVerificationToken("verify-me")).thenReturn(Optional.of(user));

        String result = service.verifyEmail("verify-me");

        assertThat(result).contains("Email vérifié avec succès");
        verify(userRepo).save(user);
        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getVerificationToken()).isNull();
    }

    @Test
    @DisplayName("Vérification email avec token invalide → exception")
    void verifyEmail_invalidToken_throws() {
        when(userRepo.findByVerificationToken("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verifyEmail("bad"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalide");
    }

    // ── getProfile ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Profil utilisateur retourné correctement")
    void getProfile_success() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));
        when(companyRoleRepo.findByCompanyIdAndUserId(companyId, userId))
            .thenReturn(Optional.of(CompanyRole.builder().role(CompanyRole.Role.ADMIN).build()));
        when(simRepo.countByUserId(userId)).thenReturn(5L);
        when(keyRepo.findByUserIdAndActiveTrue(userId)).thenReturn(List.of());

        var profile = service.getProfile(userId);

        assertThat(profile.get("email")).isEqualTo("test@example.com");
        assertThat(profile.get("full_name")).isEqualTo("Test User");
        assertThat(profile.get("company")).isEqualTo("TestCo");
        assertThat(profile.get("plan")).isEqualTo("FREE");
        assertThat(profile.get("role")).isEqualTo("ADMIN");
        assertThat(profile.get("total_simulations")).isEqualTo(5L);
        assertThat(profile.get("active_keys")).isEqualTo(0);
    }

    @Test
    @DisplayName("Profil avec userId inconnu → exception")
    void getProfile_notFound_throws() {
        when(userRepo.findById(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getProfile(UUID.randomUUID()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── updateProfile ────────────────────────────────────────────────────

    @Test
    @DisplayName("Mise à jour du profil réussie")
    void updateProfile_success() {
        when(userRepo.findById(userId)).thenReturn(Optional.of(user));

        service.updateProfile(userId, "Nouveau Nom");

        assertThat(user.getFullName()).isEqualTo("Nouveau Nom");
        verify(userRepo).save(user);
    }
}
