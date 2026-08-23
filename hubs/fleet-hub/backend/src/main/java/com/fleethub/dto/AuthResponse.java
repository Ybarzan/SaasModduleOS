package com.fleethub.dto;

public record AuthResponse(
        String token,
        String username,
        String displayName,
        String role,
        String email,
        Long companyId,
        String companyName,
        String plan,
        String companyStatus,
        boolean subscriptionActive,
        boolean totpRequired,
        boolean totpEnabled
) {}
