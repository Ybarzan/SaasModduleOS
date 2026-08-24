package com.incokalk.dto.notification;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRuleDTO {

    @NotBlank(message = "Le nom est obligatoire")
    private String name;

    @NotBlank(message = "Le type d'événement est obligatoire")
    private String eventType;

    private Boolean isActive;
    private Boolean sendEmail;
    private Boolean sendWebhook;
    private Boolean sendInApp;
    private String emailRecipients;
    private String webhookUrl;
    private String webhookSecret;
    private String filterStatus;
    private UUID filterCarrierId;
    /** LIVE, MANUAL, ou null (pas de filtre) -- voir TrackingEvent.DataSource. */
    private String filterDataSource;
    /** Arbre de condition composee (RuleConditionNode) serialise en JSON, ou null. */
    private String conditionJson;
}
