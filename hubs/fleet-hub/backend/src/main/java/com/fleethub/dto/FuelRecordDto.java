package com.fleethub.dto;

import java.time.LocalDate;

public record FuelRecordDto(
        Long id,
        Long truckId,
        String truckRegistration,
        LocalDate date,
        double liters,
        double amount,
        double odometerKm
) {}
