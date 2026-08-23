package com.fleethub.dto;

import java.time.LocalDateTime;

public record AuditLogDto(
        Long id,
        Long companyId,
        Long userId,
        String username,
        String action,
        String detail,
        String ipAddress,
        LocalDateTime createdAt) {
}
