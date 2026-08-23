package com.incokalk.dto.shipment;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class PackagingRequest {

    @NotEmpty(message = "La liste des articles est obligatoire")
    private List<PackagingItem> items;

    private List<AvailableBox> availableBoxes;

    @Data
    public static class PackagingItem {
        private String sku;
        @NotNull private Double lengthCm;
        @NotNull private Double widthCm;
        @NotNull private Double heightCm;
        @NotNull private Double weightKg;
        @NotNull private Integer quantity;
    }

    @Data
    public static class AvailableBox {
        private String ref;
        @NotNull private Double lengthCm;
        @NotNull private Double widthCm;
        @NotNull private Double heightCm;
        @DecimalMin("0.01") private Double maxWeightKg;
    }
}
