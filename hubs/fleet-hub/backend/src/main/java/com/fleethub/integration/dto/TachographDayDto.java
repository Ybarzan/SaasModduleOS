package com.fleethub.integration.dto;

import java.time.LocalDate;

public record TachographDayDto(
        String licenseNumber,
        LocalDate date,
        double drivingHours,
        double workHours,
        double restMinutes,
        boolean compliant
) {
}
