package com.fleethub.dto;

import com.fleethub.model.NotificationRule.AlertType;

import java.time.LocalDateTime;

public record NotificationDto(
        Long id,
        AlertType type,
        String title,
        String message,
        Long entityId,
        String entityType,
        boolean read,
        LocalDateTime createdAt) {
}
