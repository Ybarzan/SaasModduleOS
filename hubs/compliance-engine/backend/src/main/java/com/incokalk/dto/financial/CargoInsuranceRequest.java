package com.incokalk.dto.financial;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CargoInsuranceRequest {
    @NotNull @DecimalMin("0.01")
    private Double goodsValue;

    @NotNull @DecimalMin("0")
    private Double weightKg;

    private String transportMode;
    private String originCountry;
    private String destinationCountry;
    private String goodsCategory;
    private String currency;
}
