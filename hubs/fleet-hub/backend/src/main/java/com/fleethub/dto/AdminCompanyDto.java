package com.fleethub.dto;

import com.fleethub.model.Company;

import java.time.LocalDateTime;

public record AdminCompanyDto(
        Long id,
        String name,
        String plan,
        String status,
        LocalDateTime trialEndsAt,
        LocalDateTime createdAt,
        String contactEmail,
        String contactPhone,
        String legalName,
        String siret,
        String vatNumber,
        String city,
        String country,
        long userCount,
        long driverCount,
        long truckCount,
        boolean loginAllowed
) {
    public static AdminCompanyDto from(Company c, long userCount, long driverCount, long truckCount) {
        return new AdminCompanyDto(
                c.getId(), c.getName(), c.getPlan().name(), c.getStatus().name(),
                c.getTrialEndsAt(), c.getCreatedAt(),
                c.getContactEmail(), c.getContactPhone(),
                c.getLegalName(), c.getSiret(), c.getVatNumber(),
                c.getCity(), c.getCountry(),
                userCount, driverCount, truckCount, c.canLogin());
    }
}
