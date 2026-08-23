package com.fleethub.controller;

import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.model.AppUser;
import com.fleethub.repository.AppUserRepository;
import com.fleethub.security.TokenRevocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Gestion des utilisateurs au niveau plateforme (rôle SAAS_ADMIN).
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@Tag(name = "Administration utilisateurs", description = "Gestion des utilisateurs (SAAS_ADMIN)")
public class AdminUserController {

    private final AppUserRepository userRepository;
    private final TokenRevocationService tokenRevocationService;

    @PostMapping("/{id}/revoke-all")
    @Operation(summary = "Révoquer tous les tokens d'un utilisateur",
            description = "Déconnecte immédiatement l'utilisateur de tous ses appareils en révoquant tous ses tokens JWT actifs et à venir")
    @ApiResponse(responseCode = "200", description = "Tokens révoqués avec succès")
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable")
    public ResponseEntity<Map<String, String>> revokeAll(@PathVariable Long id) {
        AppUser user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur introuvable"));
        tokenRevocationService.revokeAllForUser(user.getId());
        return ResponseEntity.ok(Map.of(
                "message", "Tous les tokens de l'utilisateur " + user.getUsername() + " ont été révoqués"));
    }
}
