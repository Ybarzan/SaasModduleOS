package com.fleethub.dto;

import java.time.LocalDateTime;
import java.util.List;

public record PointageStatusDto(
        String state,               // IDLE | ACTIVE | PAUSE
        String driverName,
        LocalDateTime serviceStartedAt,     // début du service ouvert (null si IDLE)
        LocalDateTime segmentStartedAt,     // début du segment courant (conduite ou pause)
        long continuousDrivingSeconds,      // conduite continue depuis la dernière pause qualifiante
        long pauseSeconds,                  // durée de la pause en cours (0 hors pause)
        List<Event> todayEvents
) {
    public record Event(String type, LocalDateTime occurredAt) {}
}
