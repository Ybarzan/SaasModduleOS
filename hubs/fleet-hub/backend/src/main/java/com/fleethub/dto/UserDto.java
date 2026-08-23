package com.fleethub.dto;

import java.time.LocalDateTime;

public record UserDto(
        Long id,
        String username,
        String email,
        String displayName,
        String role,
        boolean enabled,
        LocalDateTime createdAt
) {}
