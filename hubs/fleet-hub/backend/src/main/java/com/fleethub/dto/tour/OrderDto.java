package com.fleethub.dto.tour;

import com.fleethub.model.DeliveryOrder;
import com.fleethub.model.Site;

import java.time.LocalDate;
import java.time.LocalTime;

public record OrderDto(
        Long id,
        LocalDate date,
        String reference,
        Long siteId,
        String siteName,
        String siteKind,
        String address,
        String city,
        Double latitude,
        Double longitude,
        String type,
        LocalTime windowStart,
        LocalTime windowEnd,
        int serviceMinutes,
        int quantity,
        Double temperatureMinCelsius,
        Double temperatureMaxCelsius,
        String notes,
        String status,
        Long tourId,
        String tourName
) {
    public static OrderDto of(DeliveryOrder o) {
        Site s = o.getSite();
        return new OrderDto(o.getId(), o.getDate(), o.getReference(), s.getId(), s.getName(), s.getKind().name(),
                s.getAddress(), s.getCity(), s.getLatitude(), s.getLongitude(), o.getType().name(),
                o.getWindowStart(), o.getWindowEnd(), o.getServiceMinutes(), o.getQuantity(),
                o.getTemperatureMinCelsius(), o.getTemperatureMaxCelsius(), o.getNotes(), o.getStatus().name(),
                o.getTourStop() != null ? o.getTourStop().getTour().getId() : null,
                o.getTourStop() != null ? o.getTourStop().getTour().getName() : null);
    }
}
