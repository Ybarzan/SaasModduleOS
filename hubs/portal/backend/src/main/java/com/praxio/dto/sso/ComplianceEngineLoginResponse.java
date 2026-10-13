package com.praxio.dto.sso;

/** Reflet de la réponse POST /api/v1/auth/login de compliance-engine. */
public record ComplianceEngineLoginResponse(
        String token,
        String refreshToken,
        String userId,
        String email,
        String plan,
        String role,
        String fullName
) {}
