package com.fleethub.dto;

import java.time.LocalDateTime;

/** Un événement de pointage, vu côté gestionnaire (nom du chauffeur inclus). */
public record PointageAdminEventDto(
        Long driverId,
        String driverName,
        String type,
        LocalDateTime occurredAt
) {}
