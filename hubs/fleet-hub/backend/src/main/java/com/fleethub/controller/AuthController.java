package com.fleethub.controller;

import com.fleethub.dto.AcceptInvitationRequest;
import com.fleethub.dto.AuthResponse;
import com.fleethub.dto.ForgotPasswordRequest;
import com.fleethub.dto.LoginRequest;
import com.fleethub.dto.RegisterRequest;
import com.fleethub.dto.ResetPasswordRequest;
import com.fleethub.security.JwtService;
import com.fleethub.security.TokenRevocationService;
import com.fleethub.service.AuthService;
import com.fleethub.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Gestion de l'authentification et des sessions")
public class AuthController {

    private static final String TOKEN_COOKIE = "fh_token";

    private final AuthService authService;
    private final UserService userService;
    private final JwtService jwtService;
    private final TokenRevocationService tokenRevocationService;
    private final org.springframework.security.core.userdetails.UserDetailsService userDetailsService;
    private final org.springframework.core.env.Environment environment;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    @PostMapping("/login")
    @Operation(summary = "Connexion", description = "Authentifie un utilisateur et retourne un jeton JWT")
    @ApiResponse(responseCode = "200", description = "Connexion réussie")
    @ApiResponse(responseCode = "401", description = "Identifiants incorrects")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletResponse response) {
        AuthResponse auth = authService.login(request);
        // Si 2FA requis, pas de token à envoyer en cookie
        if (auth.totpRequired()) {
            return ResponseEntity.ok(auth);
        }
        addTokenCookie(response, auth.token());
        return ResponseEntity.ok(auth);
    }

    @PostMapping("/register")
    @Operation(summary = "Inscription", description = "Crée un nouveau compte utilisateur et retourne un jeton JWT")
    @ApiResponse(responseCode = "201", description = "Compte créé avec succès")
    @ApiResponse(responseCode = "409", description = "Adresse e-mail déjà utilisée")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletResponse response) {
        AuthResponse auth = authService.register(request);
        addTokenCookie(response, auth.token());
        return ResponseEntity.status(HttpStatus.CREATED).body(auth);
    }

    @PostMapping("/logout")
    @Operation(summary = "Déconnexion", description = "Révoque le jeton et supprime le cookie")
    @ApiResponse(responseCode = "200", description = "Déconnexion réussie")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String token = extractBearerToken(request);
        if (token == null) {
            token = extractCookie(request);
        }
        if (token != null) {
            tokenRevocationService.revoke(token);
        }
        clearTokenCookie(response);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/refresh")
    @Operation(summary = "Renouvellement du jeton", description = "Rafraîchit le jeton d'authentification à partir du cookie")
    @ApiResponse(responseCode = "200", description = "Jeton renouvelé avec succès")
    @ApiResponse(responseCode = "401", description = "Jeton invalide ou expiré")
    public ResponseEntity<Void> refresh(HttpServletRequest request, HttpServletResponse response) {
        String token = extractCookie(request);
        if (token == null || !jwtService.isRefreshable(token, 300_000)) {
            clearTokenCookie(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String tokenId = jwtService.extractTokenId(token);
        if (tokenId != null && tokenRevocationService.isRevoked(tokenId)) {
            clearTokenCookie(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        String username = jwtService.extractUsername(token);
        var userDetails = userDetailsService.loadUserByUsername(username);
        if (userDetails == null || !userDetails.isEnabled()) {
            clearTokenCookie(response);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // Révoquer l'ancien token avant d'en émettre un nouveau
        tokenRevocationService.revoke(token);

        String role = jwtService.extractRole(token);
        Long companyId = jwtService.extractCompanyId(token);
        String newToken = jwtService.generateToken(username, role, companyId);
        addTokenCookie(response, newToken);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/accept-invitation")
    @Operation(summary = "Accepter une invitation", description = "Accepte une invitation et crée le compte utilisateur")
    @ApiResponse(responseCode = "200", description = "Invitation acceptée avec succès")
    @ApiResponse(responseCode = "400", description = "Lien d'invitation invalide ou expiré")
    public ResponseEntity<Void> acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request) {
        userService.acceptInvitation(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Mot de passe oublié", description = "Envoie un e-mail de réinitialisation du mot de passe")
    @ApiResponse(responseCode = "200", description = "E-mail de réinitialisation envoyé")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Réinitialiser le mot de passe", description = "Modifie le mot de passe à l'aide du jeton de réinitialisation")
    @ApiResponse(responseCode = "200", description = "Mot de passe réinitialisé avec succès")
    @ApiResponse(responseCode = "400", description = "Jeton de réinitialisation invalide ou expiré")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.ok().build();
    }

    private void addTokenCookie(HttpServletResponse response, String token) {
        Cookie cookie = new Cookie(TOKEN_COOKIE, token);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(environment.matchesProfiles("prod"));
        cookie.setMaxAge((int) (expirationMs / 1000));
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }

    private void clearTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(TOKEN_COOKIE, "");
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(environment.matchesProfiles("prod"));
        cookie.setMaxAge(0);
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }

    private String extractCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (TOKEN_COOKIE.equals(c.getName())) return c.getValue();
        }
        return null;
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        return null;
    }
}
