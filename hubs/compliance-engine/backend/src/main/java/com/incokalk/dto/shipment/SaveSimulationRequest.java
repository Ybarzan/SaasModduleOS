package com.incokalk.dto.shipment;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SaveSimulationRequest {

    @NotNull
    private Long incotermId;

    @NotNull
    private String originCountry;

     @NotNull
    private String destinationCountry;

    @NotNull
    private Double productValue;

    private Double transportCost;
    private Double insuranceCost;
    private Double customsDuty;
    private Double handlingCost;
    private Double totalCost;
    private String currency;
}