package com.fleethub.dto;

import java.time.LocalDateTime;

public record PointageSummaryDto(
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        long totalDrivingSeconds,
        long totalPauseSeconds,
        int pauseCount,
        boolean compliant
) {}
