package com.fleethub.dto;

import java.time.LocalDateTime;

public record BillingStatusDto(
        String plan,
        String status,
        LocalDateTime trialEndsAt,
        String subscriptionProvider,
        String subscriptionId,
        Integer maxVehicles,
        Integer maxDrivers,
        boolean stripeConfigured
) {
}
