package com.fleethub.dto;

import java.time.LocalDate;

public record AssignmentDto(
        Long id,
        Long driverId,
        String driverName,
        Long truckId,
        String truckRegistration,
        LocalDate startDate,
        LocalDate endDate,
        boolean active
) {}
