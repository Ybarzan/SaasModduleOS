package com.fleethub.dto;

import java.time.LocalDateTime;

public record TripDto(
        Long id,
        Long driverId,
        String driverName,
        Long truckId,
        String truckRegistration,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Double distanceKm,
        Double cargoWeightTons,
        boolean loaded,
        String status,
        boolean onTime
) {}
