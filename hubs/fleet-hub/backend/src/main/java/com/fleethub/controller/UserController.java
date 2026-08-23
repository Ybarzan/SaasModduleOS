package com.fleethub.controller;

import com.fleethub.dto.InviteUserRequest;
import com.fleethub.dto.InviteUserResponse;
import com.fleethub.dto.UpdateUserRequest;
import com.fleethub.dto.UserDto;
import com.fleethub.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Gestion des utilisateurs de la société courante (ADMIN du tenant uniquement,
 * cf. SecurityConfig). L'acceptation d'invitation est une route publique
 * {@code POST /api/auth/accept-invitation} (voir AuthController).
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Gestion des utilisateurs", description = "Gestion des utilisateurs de la societe (invitations, modification, suppression)")
public class UserController {

    private final UserService userService;

    @GetMapping
    @Operation(summary = "Lister les utilisateurs", description = "Retourne la liste des utilisateurs de la société courante")
    @ApiResponse(responseCode = "200", description = "Liste retournée avec succès")
    public List<UserDto> list() {
        return userService.list(com.fleethub.security.TenantContext.require().getId());
    }

    @PostMapping("/invite")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Inviter un utilisateur", description = "Envoie une invitation par e-mail pour rejoindre la société")
    @ApiResponse(responseCode = "201", description = "Invitation envoyée avec succès")
    @ApiResponse(responseCode = "409", description = "Adresse e-mail déjà associée à un compte")
    public InviteUserResponse invite(@Valid @RequestBody InviteUserRequest request) {
        return userService.invite(com.fleethub.security.TenantContext.require().getId(), request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un utilisateur", description = "Met à jour les informations et le rôle d'un utilisateur")
    @ApiResponse(responseCode = "200", description = "Utilisateur mis à jour avec succès")
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return userService.update(com.fleethub.security.TenantContext.require().getId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprimer un utilisateur", description = "Supprime un utilisateur de la société")
    @ApiResponse(responseCode = "204", description = "Utilisateur supprimé avec succès")
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable")
    public void delete(@PathVariable Long id) {
        userService.delete(com.fleethub.security.TenantContext.require().getId(), id);
    }
}
