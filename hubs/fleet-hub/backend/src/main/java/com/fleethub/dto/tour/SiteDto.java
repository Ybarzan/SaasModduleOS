package com.fleethub.dto.tour;

import com.fleethub.model.Site;

import java.time.LocalTime;

public record SiteDto(
        Long id,
        String name,
        String kind,
        String reference,
        String address,
        String postalCode,
        String city,
        Double latitude,
        Double longitude,
        String contactName,
        String contactPhone,
        LocalTime openingFrom,
        LocalTime openingTo,
        Integer serviceMinutes,
        String notes,
        boolean active
) {
    public static SiteDto of(Site s) {
        return new SiteDto(s.getId(), s.getName(), s.getKind().name(), s.getReference(), s.getAddress(),
                s.getPostalCode(), s.getCity(), s.getLatitude(), s.getLongitude(), s.getContactName(),
                s.getContactPhone(), s.getOpeningFrom(), s.getOpeningTo(), s.getServiceMinutes(),
                s.getNotes(), s.isActive());
    }
}
