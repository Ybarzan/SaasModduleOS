package com.fleethub.dto;

import java.util.List;

public record PointageRosterDto(
        String companyName,
        List<Driver> drivers
) {
    /** {@code pinLength} : nombre de chiffres que le clavier du portail attend avant de valider. */
    public record Driver(Long id, String firstName, String lastName, String loginUsername, int pinLength) {}
}
