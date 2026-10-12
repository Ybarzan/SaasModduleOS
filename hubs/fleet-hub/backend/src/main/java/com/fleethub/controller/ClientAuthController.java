package com.fleethub.controller;

import com.fleethub.security.ClientAuthFilter;
import com.fleethub.service.ClientAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.UUID;

/**
 * Authentification du portail client — porté depuis compliance-engine.
 * Système d'authentification séparé et parallèle à celui du staff FleetHub
 * (ClientUser, jamais un compte AppUser) ; voir {@link ClientAuthFilter}.
 */
@RestController
@RequestMapping("/api/client/auth")
@RequiredArgsConstructor
@Tag(name = "Client Auth", description = "Authentification du portail client")
public class ClientAuthController {

    private final ClientAuthService clientAuthService;

    @PostMapping("/login")
    @Operation(summary = "Connexion client")
    public ResponseEntity<Map<String, Object>> login(@Valid @RequestBody LoginReq req) {
        var r = clientAuthService.login(req.email(), req.password());
        return ResponseEntity.ok(Map.of(
            "token", r.token(),
            "clientId", r.clientId(),
            "email", r.email(),
            "fullName", r.fullName() != null ? r.fullName() : "",
            "companyId", r.companyId()
        ));
    }

    @GetMapping("/me")
    @Operation(summary = "Profil client")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest req) {
        UUID clientId = ClientAuthFilter.clientId(req);
        if (clientId == null) throw new ResponseStatusException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Non authentifié");
        return ResponseEntity.ok(clientAuthService.getProfile(clientId));
    }

    record LoginReq(@Email @NotBlank String email, @NotBlank String password) {}
}
