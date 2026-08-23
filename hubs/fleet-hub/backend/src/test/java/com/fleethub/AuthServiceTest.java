package com.fleethub;

import com.fleethub.dto.AuthResponse;
import com.fleethub.dto.LoginRequest;
import com.fleethub.dto.RegisterRequest;
import com.fleethub.model.AppUser;
import com.fleethub.model.Company;
import com.fleethub.repository.AppUserRepository;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.security.JwtService;
import com.fleethub.security.TwoFactorService;
import com.fleethub.service.AuthService;
import com.fleethub.service.AuditService;
import com.fleethub.service.email.EmailNotifier;
import com.fleethub.service.email.MailProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private JwtService jwtService;
    @Mock
    private AppUserRepository userRepository;
    @Mock
    private CompanyRepository companyRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private EmailNotifier emailNotifier;
    @Mock
    private MailProperties mailProperties;
    @Mock
    private AuditService auditService;
    @Mock
    private TwoFactorService twoFactorService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(authenticationManager, jwtService, userRepository,
                companyRepository, passwordEncoder, emailNotifier, mailProperties, auditService, twoFactorService);
        ReflectionTestUtils.setField(authService, "trialDays", 14);
    }

    private Company demoCompany() {
        Company c = new Company();
        c.setId(1L);
        c.setName("Démo");
        c.setPlan(Company.SubscriptionPlan.PRO);
        c.setStatus(Company.CompanyStatus.ACTIVE);
        c.setCreatedAt(LocalDateTime.now());
        return c;
    }

    private AppUser user(Company company) {
        AppUser u = new AppUser();
        u.setId(1L);
        u.setUsername("admin");
        u.setPassword("hash");
        u.setRole("ADMIN");
        u.setDisplayName("Admin");
        u.setEmail("admin@demo.fr");
        u.setCompany(company);
        return u;
    }

    @Test
    void login_success_returnsTokenRoleAndCompany() {
        Company company = demoCompany();
        AppUser user = user(company);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("admin", "admin"));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user));
        when(jwtService.generateToken("admin", "ADMIN", 1L)).thenReturn("jwt-token");

        AuthResponse res = authService.login(new LoginRequest("admin", "admin", null));

        assertEquals("jwt-token", res.token());
        assertEquals("admin", res.username());
        assertEquals("ADMIN", res.role());
        assertEquals(1L, res.companyId());
        assertEquals("Démo", res.companyName());
        assertEquals("PRO", res.plan());
        assertTrue(res.subscriptionActive());
    }

    @Test
    void login_badCredentials_propagates() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad"));
        assertThrows(BadCredentialsException.class,
                () -> authService.login(new LoginRequest("admin", "wrong", null)));
    }

    @Test
    void login_unknownUser_throws() {
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("ghost", "x"));
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        assertThrows(UsernameNotFoundException.class,
                () -> authService.login(new LoginRequest("ghost", "x", null)));
    }

    @Test
    void login_suspendedCompany_isAllowedButDataFrozen() {
        Company company = demoCompany();
        company.setStatus(Company.CompanyStatus.SUSPENDED);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("admin", "admin"));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(company)));
        when(jwtService.generateToken("admin", "ADMIN", 1L)).thenReturn("jwt-token");

        AuthResponse res = authService.login(new LoginRequest("admin", "admin", null));

        assertEquals("jwt-token", res.token());
        assertEquals("SUSPENDED", res.companyStatus());
        assertFalse(res.subscriptionActive());
    }

    @Test
    void login_expiredTrial_isAllowedButDataFrozen() {
        Company company = demoCompany();
        company.setStatus(Company.CompanyStatus.TRIAL);
        company.setPlan(Company.SubscriptionPlan.TRIAL);
        company.setTrialEndsAt(LocalDateTime.now().minusDays(1));
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("admin", "admin"));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(company)));
        when(jwtService.generateToken("admin", "ADMIN", 1L)).thenReturn("jwt-token");

        AuthResponse res = authService.login(new LoginRequest("admin", "admin", null));

        assertEquals("TRIAL", res.plan());
        assertFalse(res.subscriptionActive());
    }

    @Test
    void login_cancelledCompany_isForbidden() {
        Company company = demoCompany();
        company.setStatus(Company.CompanyStatus.CANCELLED);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("admin", "admin"));
        when(userRepository.findByUsername("admin")).thenReturn(Optional.of(user(company)));

        assertThrows(ResponseStatusException.class,
                () -> authService.login(new LoginRequest("admin", "admin", null)));
    }

    @Test
    void register_createsCompanyAndOwner() {
        when(userRepository.findByUsername("a@b.fr")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("password123")).thenReturn("hash");
        when(jwtService.generateToken("a@b.fr", "ADMIN", null)).thenReturn("jwt-token");

        AuthResponse res = authService.register(
                new RegisterRequest("Ma Société", "Alice", "Martin", "a@b.fr", "password123"));

        assertEquals("jwt-token", res.token());
        assertEquals("a@b.fr", res.username());
        assertEquals("ADMIN", res.role());
        assertEquals("Alice Martin", res.displayName());
        assertEquals("TRIAL", res.plan());
    }

    @Test
    void register_duplicateEmail_conflict() {
        when(userRepository.findByUsername("a@b.fr")).thenReturn(Optional.of(new AppUser()));
        assertThrows(ResponseStatusException.class,
                () -> authService.register(
                        new RegisterRequest("Ma Société", "Alice", "Martin", "a@b.fr", "password123")));
    }
}
