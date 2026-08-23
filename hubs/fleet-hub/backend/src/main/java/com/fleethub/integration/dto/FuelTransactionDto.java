package com.fleethub.integration.dto;

import java.time.LocalDate;

public record FuelTransactionDto(
        String registration,
        LocalDate date,
        double liters,
        double amount,
        double odometerKm
) {
}
