package com.logistix.controller;

import com.logistix.dto.AuthResponse;
import com.logistix.dto.LoginRequest;
import com.logistix.dto.RegisterRequest;
import com.logistix.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentification", description = "Connexion et inscription")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Connexion")
    @ApiResponse(responseCode = "200", description = "Connexion réussie")
    @ApiResponse(responseCode = "401", description = "Identifiants incorrects")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    @Operation(summary = "Inscription")
    @ApiResponse(responseCode = "201", description = "Compte créé avec succès")
    @ApiResponse(responseCode = "409", description = "Adresse e-mail déjà utilisée")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }
}
