package com.incokalk.dto.auth;

import java.util.List;

public record RoleResponse(
        String id,
        String name,
        String description,
        long userCount,
        List<String> permissions,
        boolean isSystem
) {}
