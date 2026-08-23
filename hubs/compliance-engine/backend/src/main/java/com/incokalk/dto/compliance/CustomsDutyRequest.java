package com.incokalk.dto.compliance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CustomsDutyRequest {
    @NotBlank(message = "Le code HS est obligatoire")
    private String hsCode;

    @NotBlank(message = "Le pays d'origine est obligatoire")
    private String originCountry;

    @NotBlank(message = "Le pays de destination est obligatoire")
    private String destinationCountry;

    @NotNull @DecimalMin("0.01")
    private Double goodsValue;

    @DecimalMin("0")
    private Double freightCost;

    @DecimalMin("0")
    private Double insuranceCost;

    private String currency;
}
