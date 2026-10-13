package com.praxio.dto.sso;

/** Reflet de la réponse POST /v1/auth/login de Finance (AuthResponse). */
public record FinanceLoginResponse(
        String token,
        String email,
        String displayName,
        String role,
        String companyId,
        String companyName
) {}
