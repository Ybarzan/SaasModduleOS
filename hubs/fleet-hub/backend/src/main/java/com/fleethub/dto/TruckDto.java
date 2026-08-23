package com.fleethub.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record TruckDto(
        Long id,
        String registration,
        String brand,
        String model,
        Integer year,
        String truckType,
        String fuelType,
        Double capacityTons,
        LocalDate acquisitionDate,
        Double purchasePrice,
        Double expectedConsumptionL100Km,
        String currentStatus,
        LocalDateTime lastGpsUpdate,
        boolean active,
        Long assignmentId,
        Long driverId,
        String driverName
) {}
