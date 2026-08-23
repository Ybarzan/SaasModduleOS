package com.fleethub.dto;

import com.fleethub.model.Company;

import java.time.LocalDateTime;

public record CompanyDto(
        Long id,
        String name,
        String plan,
        String status,
        LocalDateTime trialEndsAt,
        LocalDateTime createdAt,
        String subscriptionProvider,
        String subscriptionId
) {
    public static CompanyDto from(Company c) {
        return new CompanyDto(c.getId(), c.getName(), c.getPlan().name(), c.getStatus().name(),
                c.getTrialEndsAt(), c.getCreatedAt(), c.getSubscriptionProvider(), c.getSubscriptionId());
    }
}
