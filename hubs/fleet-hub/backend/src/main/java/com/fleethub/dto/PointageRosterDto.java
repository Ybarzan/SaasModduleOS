package com.fleethub.dto;

import java.util.List;

public record PointageRosterDto(
        String companyName,
        List<Driver> drivers
) {
    public record Driver(Long id, String firstName, String lastName, String loginUsername) {}
}
