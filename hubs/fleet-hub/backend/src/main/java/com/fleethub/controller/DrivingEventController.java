package com.fleethub.controller;

import com.fleethub.dto.DrivingEventDto;
import com.fleethub.repository.DrivingEventRepository;
import com.fleethub.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/driving-events")
@RequiredArgsConstructor
@Tag(name = "Événements de conduite", description = "Alertes et événements de conduite (freinage, accélération, vitesse)")
public class DrivingEventController {

    private final DrivingEventRepository eventRepository;

    @GetMapping
    @Operation(summary = "Lister les événements", description = "Retourne les événements de conduite avec filtres optionnels")
    public List<DrivingEventDto> all(@RequestParam(required = false) Long driverId,
                                     @RequestParam(required = false) Long truckId,
                                     @RequestParam(required = false) String type,
                                     @RequestParam(required = false) String from,
                                     @RequestParam(required = false) String to) {
        LocalDateTime fromTs = from != null ? LocalDateTime.parse(from) : null;
        LocalDateTime toTs = to != null ? LocalDateTime.parse(to) : null;
        return eventRepository.findAllFetch(TenantContext.companyId()).stream()
                .filter(e -> driverId == null || e.getDriver().getId().equals(driverId))
                .filter(e -> truckId == null || e.getTruck().getId().equals(truckId))
                .filter(e -> type == null || e.getType().name().equals(type))
                .filter(e -> fromTs == null || !e.getTimestamp().isBefore(fromTs))
                .filter(e -> toTs == null || !e.getTimestamp().isAfter(toTs))
                .map(e -> new DrivingEventDto(
                        e.getId(),
                        e.getDriver().getId(),
                        e.getDriver().getFirstName() + " " + e.getDriver().getLastName(),
                        e.getTruck().getId(),
                        e.getTruck().getRegistration(),
                        e.getTimestamp(), e.getType().name(), e.getSeverity(),
                        e.getSpeedKph(), e.getDurationSec()))
                .toList();
    }
}
