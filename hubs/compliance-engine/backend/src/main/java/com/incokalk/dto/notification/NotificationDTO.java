package com.incokalk.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDTO {

    private UUID id;
    private String eventType;
    private String title;
    private String message;
    private String channel;
    private String status;
    private LocalDateTime sentAt;
    private LocalDateTime readAt;
    private String entityType;
    private UUID entityId;
    private String webhookStatus;
    private Integer webhookResponseCode;
    private LocalDateTime createdAt;
    private String ruleName;
    private UUID userId;
}
