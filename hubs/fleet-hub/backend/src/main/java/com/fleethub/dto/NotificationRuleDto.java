package com.fleethub.dto;

import com.fleethub.model.NotificationRule.AlertType;

public record NotificationRuleDto(Long id, AlertType type, Double threshold, boolean enabled) {
}
