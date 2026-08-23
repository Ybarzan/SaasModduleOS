package com.incokalk.dto.shipment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarrierDTO {

    @NotBlank(message = "Le nom est obligatoire")
    private String name;

    @NotBlank(message = "Le code est obligatoire")
    @Size(min = 2, max = 50, message = "Le code doit contenir entre 2 et 50 caractères")
    private String code;

    private String logoUrl;

    @NotBlank(message = "Les modes de transport sont obligatoires (ex: SEA,AIR,ROAD)")
    private String transportModes;

    private String apiEndpoint;

    private String contactName;

    private String contactEmail;

    private String contactPhone;

    @Size(min = 2, max = 3)
    private String country;

    private Boolean isActive;
}
