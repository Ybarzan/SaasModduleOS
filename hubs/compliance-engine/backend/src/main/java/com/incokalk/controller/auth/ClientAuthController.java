package com.incokalk.controller.auth;

import com.incokalk.service.ClientAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/v1/client/auth")
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
            "fullName", r.fullName(),
            "companyId", r.companyId()
        ));
    }

    @GetMapping("/me")
    @Operation(summary = "Profil client")
    public ResponseEntity<Map<String, Object>> me(HttpServletRequest req) {
        UUID clientId = extractClientId(req);
        return ResponseEntity.ok(clientAuthService.getProfile(clientId));
    }

    private UUID extractClientId(HttpServletRequest req) {
        Object id = req.getAttribute("userId");
        if (id == null) throw new RuntimeException("Non authentifié");
        return id instanceof UUID u ? u : UUID.fromString(id.toString());
    }

    record LoginReq(@Email @NotBlank String email, @NotBlank String password) {}
}
