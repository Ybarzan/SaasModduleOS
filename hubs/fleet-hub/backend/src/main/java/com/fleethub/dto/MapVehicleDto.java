package com.fleethub.dto;

import java.time.LocalDateTime;

public record MapVehicleDto(
        Long truckId,
        String registration,
        String brand,
        String model,
        String driverName,
        Long assignmentId,
        double latitude,
        double longitude,
        double speedKph,
        String status,
        LocalDateTime lastGpsUpdate
) {}
