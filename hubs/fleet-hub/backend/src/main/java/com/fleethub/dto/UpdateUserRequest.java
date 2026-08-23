package com.fleethub.dto;

public record UpdateUserRequest(
        String displayName,
        String role,
        Boolean enabled
) {}
