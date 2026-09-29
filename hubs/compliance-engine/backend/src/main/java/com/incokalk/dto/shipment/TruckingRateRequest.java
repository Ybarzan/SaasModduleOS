package com.incokalk.dto.shipment;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class TruckingRateRequest {

    @NotBlank
    private String originCountry;

    @NotBlank
    private String destinationCountry;

    @DecimalMin("0.01")
    private Double weightKg;

    @DecimalMin("0.01")
    private Double volumeM3;

    private Integer palletCount;

    /** Distance routière réelle (km). Sans elle, aucun délai de transit n'est calculé. */
    @DecimalMin("1")
    private Double distanceKm;
    private Double goodsValue;
    private String currency;
}
