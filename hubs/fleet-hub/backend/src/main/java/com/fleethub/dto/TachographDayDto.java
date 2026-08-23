package com.fleethub.dto;

import java.time.LocalDate;
import java.util.List;

public record TachographDayDto(
        Long id,
        Long driverId,
        String driverName,
        LocalDate date,
        double drivingHours,
        double workHours,
        double restMinutes,
        boolean compliant,
        List<String> reasons
) {}
