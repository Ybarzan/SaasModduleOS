package com.fleethub.dto;

import com.fleethub.model.Company;

public record CompanyProfileDto(
        Long id,
        String name,
        String plan,
        String status,
        String fleetProfile,
        /** Le module Tournées est mis en avant pour ce profil. */
        boolean toursEnabled
) {
    public static CompanyProfileDto of(Company c) {
        Company.FleetProfile profile = c.getFleetProfile() != null ? c.getFleetProfile() : Company.FleetProfile.POIDS_LOURD;
        return new CompanyProfileDto(c.getId(), c.getName(), c.getPlan().name(), c.getStatus().name(),
                profile.name(), profile.usesTours());
    }
}
