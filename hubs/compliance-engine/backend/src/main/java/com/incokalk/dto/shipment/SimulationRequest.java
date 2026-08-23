package com.incokalk.dto.shipment;

import com.incokalk.model.Incoterm;
import com.incokalk.validation.IsoCountryCode;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class SimulationRequest {

    @NotNull(message = "L'Incoterm est obligatoire")
    private Incoterm incoterm;

    @NotBlank(message = "Pays d'origine obligatoire")
    @IsoCountryCode
    private String originCountry;

    @NotBlank(message = "Pays de destination obligatoire")
    @IsoCountryCode
    private String destinationCountry;

    @NotNull
    @DecimalMin(value = "0.01", message = "La valeur doit être positive")
    private Double goodsValue;

    @NotBlank
    @Size(min = 3, max = 3)
    private String currency;

    private TransportModeInput transportMode;
    private InsuranceLevel insuranceLevel = InsuranceLevel.STANDARD;
    private String hsCode;
    private Double weightKg;
    private Double volumeM3;
    private boolean compareWithOthers = false;

    private List<PackagingItem> packagingItems;

    @Data
    public static class PackagingItem {
        private String sku;
        private Double lengthCm;
        private Double widthCm;
        private Double heightCm;
        private Double weightKg;
        private Integer quantity;
    }

    public enum TransportModeInput { SEA, AIR, ROAD, MULTIMODAL }
    public enum InsuranceLevel { MINIMUM, STANDARD, ALL_RISKS }
}
