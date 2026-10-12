package com.logistix.dto;

import java.util.UUID;

public record AuthResponse(
        String token,
        String email,
        String displayName,
        String role,
        UUID companyId,
        String companyName
) {}
