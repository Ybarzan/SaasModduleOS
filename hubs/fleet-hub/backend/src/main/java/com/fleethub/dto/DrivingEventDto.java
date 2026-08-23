package com.fleethub.dto;

import java.time.LocalDateTime;

public record DrivingEventDto(
        Long id,
        Long driverId,
        String driverName,
        Long truckId,
        String truckRegistration,
        LocalDateTime timestamp,
        String type,
        int severity,
        Double speedKph,
        Double durationSec
) {}
