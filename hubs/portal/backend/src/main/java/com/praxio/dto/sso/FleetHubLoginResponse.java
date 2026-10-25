package com.praxio.dto.sso;

/** Reflet de la réponse POST /api/auth/login de fleet-hub (AuthResponse). */
public record FleetHubLoginResponse(
        String token,
        String username,
        String displayName,
        String role,
        String email,
        Long companyId,
        String companyName,
        String companyAccessCode,
        String plan,
        String companyStatus,
        boolean subscriptionActive,
        boolean totpRequired,
        boolean totpEnabled
) {}
