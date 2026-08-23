package com.fleethub.dto;

import java.time.LocalDate;

public record DriverDto(
        Long id,
        String firstName,
        String lastName,
        String licenseNumber,
        String phone,
        String email,
        LocalDate hireDate,
        boolean active,
        Long truckId,
        String truckRegistration
) {}
