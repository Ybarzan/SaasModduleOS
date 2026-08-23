package com.fleethub.controller;

import com.fleethub.dto.MapVehicleDto;
import com.fleethub.model.DriverTruckAssignment;
import com.fleethub.repository.AssignmentRepository;
import com.fleethub.repository.TruckRepository;
import com.fleethub.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/map")
@RequiredArgsConstructor
@Tag(name = "Carte", description = "Position GPS des véhicules en temps réel")
public class MapController {

    private final TruckRepository truckRepository;
    private final AssignmentRepository assignmentRepository;

    @GetMapping("/vehicles")
    @Operation(summary = "Véhicules sur la carte", description = "Retourne les véhicules avec position GPS et chauffeur affecté")
    public List<MapVehicleDto> vehicles() {
        Long companyId = TenantContext.companyId();
        List<DriverTruckAssignment> active = assignmentRepository.findByActiveTrue(companyId);
        return truckRepository.findByCompanyId(companyId).stream()
                .filter(t -> t.getCurrentLatitude() != null && t.getCurrentLongitude() != null)
                .map(t -> {
                    DriverTruckAssignment a = active.stream()
                            .filter(x -> x.getTruck().getId().equals(t.getId())).findFirst().orElse(null);
                    String driver = a != null ? a.getDriver().getFirstName() + " " + a.getDriver().getLastName() : null;
                    return new MapVehicleDto(
                            t.getId(), t.getRegistration(), t.getBrand(), t.getModel(), driver,
                            a != null ? a.getId() : null,
                            t.getCurrentLatitude(), t.getCurrentLongitude(),
                            t.getCurrentSpeedKph() != null ? t.getCurrentSpeedKph() : 0,
                            t.getCurrentStatus() != null ? t.getCurrentStatus().name() : "ARRET",
                            t.getLastGpsUpdate());
                }).toList();
    }
}
