package com.incokalk.dto.shipment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuoteRequestDTO {

    @NotBlank(message = "Le pays d'origine est obligatoire")
    private String originCountry;

    @NotBlank(message = "Le pays de destination est obligatoire")
    private String destinationCountry;

    private String transportMode;

    @NotNull(message = "Le poids est obligatoire")
    @DecimalMin(value = "0.01", message = "Le poids doit être positif")
    private Double weightKg;

    @NotNull(message = "Le volume est obligatoire")
    @DecimalMin(value = "0.01", message = "Le volume doit être positif")
    private Double volumeM3;

    private Double goodsValue;

    private String currency;

    private String hsCode;
}
