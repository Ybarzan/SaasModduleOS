package com.fleethub.controller;

import com.fleethub.model.AppUser;
import com.fleethub.repository.AppUserRepository;
import com.fleethub.security.TenantContext;
import com.fleethub.security.TwoFactorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users/2fa")
@RequiredArgsConstructor
@Tag(name = "Double authentification", description = "Configuration TOTP (2FA)")
public class TwoFactorController {

    private final TwoFactorService twoFactorService;
    private final AppUserRepository userRepository;

    @GetMapping("/status")
    @Operation(summary = "Statut 2FA",
            description = "Indique si la double authentification est activée pour l'utilisateur courant")
    public ResponseEntity<Map<String, Object>> status(@AuthenticationPrincipal UserDetails principal) {
        AppUser user = requireUser(principal);
        return ResponseEntity.ok(Map.of(
                "totpEnabled", user.isTotpEnabled(),
                "hasSecret", user.getTotpSecret() != null));
    }

    @PostMapping("/setup")
    @Operation(summary = "Initialiser la 2FA",
            description = "Génère un secret TOTP et retourne l'URI pour le scan QR code")
    @ApiResponse(responseCode = "200", description = "Secret et QR code URI générés")
    public ResponseEntity<Map<String, String>> setup(@AuthenticationPrincipal UserDetails principal) {
        AppUser user = requireUser(principal);
        String secret = twoFactorService.generateSecret();
        user.setTotpSecret(secret);
        userRepository.save(user);

        String otpAuthUri = twoFactorService.buildOtpAuthUri(user.getUsername(), secret);
        return ResponseEntity.ok(Map.of(
                "secret", secret,
                "otpauthUri", otpAuthUri));
    }

    @PostMapping("/enable")
    @Operation(summary = "Activer la 2FA",
            description = "Valide un code TOTP et active la double authentification")
    @ApiResponse(responseCode = "200", description = "2FA activée avec succès")
    @ApiResponse(responseCode = "400", description = "Code TOTP invalide")
    public ResponseEntity<Map<String, String>> enable(@RequestBody Map<String, String> body,
                                                      @AuthenticationPrincipal UserDetails principal) {
        AppUser user = requireUser(principal);
        String code = body.get("code");
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Code requis"));
        }
        if (user.getTotpSecret() == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Veuillez d'abord initialiser la 2FA (/setup)"));
        }
        if (!twoFactorService.verifyCode(user.getTotpSecret(), code)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Code TOTP invalide"));
        }
        user.setTotpEnabled(true);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "2FA activée avec succès"));
    }

    @PostMapping("/disable")
    @Operation(summary = "Désactiver la 2FA",
            description = "Désactive la double authentification après vérification du code")
    @ApiResponse(responseCode = "200", description = "2FA désactivée")
    @ApiResponse(responseCode = "400", description = "Code TOTP invalide")
    public ResponseEntity<Map<String, String>> disable(@RequestBody Map<String, String> body,
                                                       @AuthenticationPrincipal UserDetails principal) {
        AppUser user = requireUser(principal);
        if (!user.isTotpEnabled()) {
            return ResponseEntity.badRequest().body(Map.of("message", "La 2FA n'est pas activée"));
        }
        String code = body.get("code");
        if (code == null || !twoFactorService.verifyCode(user.getTotpSecret(), code)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Code TOTP invalide"));
        }
        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("message", "2FA désactivée"));
    }

    private AppUser requireUser(UserDetails principal) {
        return userRepository.findByUsername(principal.getUsername())
                .orElseThrow(() -> new com.fleethub.config.ResourceNotFoundException("Utilisateur introuvable"));
    }
}
