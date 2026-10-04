package com.fleethub.dto.tour;

import java.util.List;

public record DispatchResultDto(
        /** Moteur utilisé : « VROOM » ou « intégré ». */
        String solver,
        List<TourDto> tours,
        List<Unassigned> unassigned,
        double totalKm
) {
    public record Unassigned(Long orderId, String reference, String siteName, String reason) {}
}
