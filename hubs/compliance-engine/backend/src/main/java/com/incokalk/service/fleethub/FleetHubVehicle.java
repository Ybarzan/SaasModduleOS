package com.incokalk.service.fleethub;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Reflet exact de com.fleethub.dto.MapVehicleDto (GET /api/map/vehicles, record
 * côté fleet-hub) -- mêmes noms/types de champ pour que Jackson désérialise
 * directement sans mapping manuel. Un véhicule de la flotte propre du client
 * avec sa position GPS courante. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FleetHubVehicle {
    private Long truckId;
    private String registration;
    private String brand;
    private String model;
    private String driverName;
    private Long assignmentId;
    private double latitude;
    private double longitude;
    private double speedKph;
    private String status;
    private LocalDateTime lastGpsUpdate;
}
