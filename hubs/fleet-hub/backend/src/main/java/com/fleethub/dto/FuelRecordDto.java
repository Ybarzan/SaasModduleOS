package com.fleethub.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FuelRecordDto(
        Long id,
        Long truckId,
        String truckRegistration,
        LocalDate date,
        double liters,
        BigDecimal amount,
        double odometerKm
) {}
