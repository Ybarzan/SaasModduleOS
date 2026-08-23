package com.incokalk.dto.shipment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class RouteOptimizationRequest {
    @NotBlank(message = "Le pays de départ est obligatoire")
    private String originCountry;

    @NotBlank(message = "Le pays d'arrivée est obligatoire")
    private String destinationCountry;

    @NotNull(message = "La liste des stops est obligatoire")
    private List<StopPoint> stops;

    private Double fuelPricePerLiter;
    private Double consumptionPer100km;
    private String vehicleType;

    @Data
    public static class StopPoint {
        @NotBlank
        private String city;
        private String country;
        private Double latitude;
        private Double longitude;
    }
}
