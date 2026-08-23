package com.incokalk.dto.auth;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CustomRoleRequest(
        @NotBlank String name,
        String description,
        List<String> permissions
) {}
