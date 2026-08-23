package com.incokalk.dto.shipment;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShippingRateDTO {

    @NotNull(message = "Le transporteur est obligatoire")
    private UUID carrierId;

    @NotBlank(message = "Le nom du tarif est obligatoire")
    @Size(max = 100)
    private String name;

    @NotBlank(message = "Le pays d'origine est obligatoire")
    @Size(min = 2, max = 3, message = "Code pays ISO 2-3 caractères")
    private String originCountry;

    @NotBlank(message = "Le pays de destination est obligatoire")
    @Size(min = 2, max = 3, message = "Code pays ISO 2-3 caractères")
    private String destinationCountry;

    @NotBlank(message = "Le mode de transport est obligatoire")
    @Pattern(regexp = "SEA|AIR|ROAD", message = "Mode de transport invalide (SEA, AIR, ROAD)")
    private String transportMode;

    @DecimalMin(value = "0", message = "Poids minimum doit être positif")
    private Double minWeightKg;

    @DecimalMin(value = "0", message = "Poids maximum doit être positif")
    private Double maxWeightKg;

    @NotNull(message = "Le tarif de base est obligatoire")
    @DecimalMin(value = "0", inclusive = false, message = "Le tarif de base doit être supérieur à 0")
    private double baseRate;

    @Size(min = 3, max = 3, message = "Code devise ISO 3 caractères")
    @Builder.Default
    private String currency = "EUR";

    private Double ratePerKg;

    private Double ratePerCbm;

    @Min(value = 1, message = "Transit minimum 1 jour")
    private Integer transitDaysMin;

    @Min(value = 1, message = "Transit maximum 1 jour")
    private Integer transitDaysMax;

    private Double co2EstimateKg;

    @Builder.Default
    private Boolean isActive = true;

    private LocalDateTime validFrom;

    private LocalDateTime validUntil;
}