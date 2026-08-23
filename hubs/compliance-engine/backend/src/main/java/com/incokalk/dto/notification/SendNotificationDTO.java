package com.incokalk.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendNotificationDTO {

    private String eventType;
    private UUID companyId;
    private UUID userId;
    private String entityType;
    private UUID entityId;
    private String title;
    private String message;
    private Map<String, String> templateData;
}
