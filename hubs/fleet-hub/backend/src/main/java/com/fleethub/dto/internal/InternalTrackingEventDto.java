package com.fleethub.dto.internal;

import com.fleethub.model.TrackingEvent;

import java.time.LocalDateTime;
import java.util.UUID;

/** Événement de suivi d'une expédition, exposé aux autres hubs (voir {@link InternalShipmentDto}). */
public record InternalTrackingEventDto(
        UUID id,
        String status,
        String location,
        Double latitude,
        Double longitude,
        String description,
        LocalDateTime eventTime,
        String source
) {
    public static InternalTrackingEventDto from(TrackingEvent e) {
        return new InternalTrackingEventDto(e.getId(), e.getStatus(), e.getLocation(), e.getLatitude(),
                e.getLongitude(), e.getDescription(), e.getEventTime(), e.getSource());
    }
}
