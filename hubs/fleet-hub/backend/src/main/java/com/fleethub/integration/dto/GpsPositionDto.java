package com.fleethub.integration.dto;

import java.time.LocalDateTime;

public record GpsPositionDto(
        String registration,
        double latitude,
        double longitude,
        double speedKph,
        LocalDateTime timestamp
) {
}
