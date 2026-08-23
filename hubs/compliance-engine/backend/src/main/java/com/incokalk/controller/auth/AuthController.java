package com.incokalk.controller.auth;

import com.incokalk.security.JwtService;
import com.incokalk.service.AuthService;
import com.incokalk.service.CustomRoleService;
import com.incokalk.tenant.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Register, Login, Profil, Forgot/Reset Password, Email Verification")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final CustomRoleService customRoleService;

    @PostMapping("/register")
    @Operation(summary = "Créer un compte")
    public ResponseEntity<Map<String, Object>> register(@Valid @RequestBody RegisterReq req) {
        var r = authService.register(req.email(), req.password(), req.fullName(), req.company(), req.referralCode());
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
            "token", r.token(), "refreshToken", r.refreshToken(), "userId", r.userId(),
            "email", r.email(), "plan", r.plan(), "role", r.role(),
            "fullName", r.fullName() != null ? r.fullName() : ""
        ));
    }

    @PostMapping("/login")
    @Operation(summary = "Se connecter")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginReq req) {
        var r = authService.login(req.email(), req.password());
        return ResponseEntity.ok(Map.of(
            "token", r.token(), "refreshToken", r.refreshToken(), "userId", r.userId(),
            "email", r.email(), "plan", r.plan(), "role", r.role(),
            "fullName", r.fullName() != null ? r.fullName() : ""
        ));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rafraîchir le token d'accès")
    public ResponseEntity<Map<String, Object>> refreshToken(@Valid @RequestBody RefreshTokenReq req) {
        var r = authService.refreshAccessToken(req.refreshToken());
        return ResponseEntity.ok(Map.of(
            "token", r.token(), "refreshToken", r.refreshToken(), "userId", r.userId(),
            "email", r.email(), "plan", r.plan(), "role", r.role(),
            "fullName", r.fullName() != null ? r.fullName() : ""
        ));
    }

    @PostMapping("/logout")
    @Operation(summary = "Se déconnecter (révoque les refresh tokens)")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest req) {
        authService.logout(extractUserId(req));
        return ResponseEntity.ok(Map.of("message", "Déconnecté"));
    }

    @GetMapping("/me")
    @Operation(summary = "Profil utilisateur")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest req) {
        return ResponseEntity.ok(authService.getProfile(extractUserId(req)));
    }

    @GetMapping("/me/permissions")
    @Operation(summary = "Permissions effectives de l'utilisateur courant (rôle + rôle personnalisé)")
    public ResponseEntity<Map<String, Object>> myPermissions(HttpServletRequest req) {
        UUID companyId = TenantContext.get();
        List<String> permissions = companyId != null
                ? customRoleService.effectivePermissions(extractUserId(req), companyId)
                : List.of();
        return ResponseEntity.ok(Map.of("permissions", permissions));
    }

    @PutMapping("/me")
    @Operation(summary = "Mettre à jour le profil")
    public ResponseEntity<Map<String, String>> updateMe(
            @Valid @RequestBody UpdateProfileReq body, HttpServletRequest req) {
        authService.updateProfile(extractUserId(req), body.fullName());
        return ResponseEntity.ok(Map.of("message", "Profil mis à jour"));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Demander un lien de réinitialisation du mot de passe")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordReq req) {
        String message = authService.forgotPassword(req.email());
        return ResponseEntity.ok(Map.of("message", message));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Réinitialiser le mot de passe avec le token")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordReq req) {
        authService.resetPassword(req.token(), req.newPassword());
        return ResponseEntity.ok(Map.of("message", "Mot de passe réinitialisé avec succès"));
    }

    @GetMapping("/verify-email")
    @Operation(summary = "Vérifier l'email avec le token")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
        String message = authService.verifyEmail(token);
        return ResponseEntity.ok(Map.of("message", message));
    }

    private UUID extractUserId(HttpServletRequest req) {
        Object id = req.getAttribute("userId");
        if (id == null) throw new RuntimeException("Non authentifié");
        return id instanceof UUID u ? u : UUID.fromString(id.toString());
    }

    record RegisterReq(@Email @NotBlank String email,
                       @NotBlank @Size(min=8) String password,
                       @NotBlank String fullName, String company, String referralCode) {}
    record LoginReq(@Email @NotBlank String email, @NotBlank String password) {}
    record RefreshTokenReq(@NotBlank String refreshToken) {}
    record UpdateProfileReq(String fullName) {}
    record ForgotPasswordReq(@Email @NotBlank String email) {}
    record ResetPasswordReq(@NotBlank String token, @NotBlank @Size(min=8) String newPassword) {}
}
