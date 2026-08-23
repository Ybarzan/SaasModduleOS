package com.incokalk.dto.shipment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackagingResult {

    private int totalBoxes;
    private double totalVolumeM3;
    private double totalWeightKg;
    private double utilizationPercent;
    private double totalPackageVolumeM3;

    private List<BoxInfo> boxes;
    private List<ItemInfo> unpackedItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BoxInfo {
        private String boxRef;
        private double lengthCm;
        private double widthCm;
        private double heightCm;
        private double volumeM3;
        private double usedVolumeM3;
        private double utilizationPercent;
        private double totalWeightKg;
        private List<PackedItem> packedItems;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PackedItem {
        private String sku;
        private int quantity;
        private double volumeM3;
        private double weightKg;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ItemInfo {
        private String sku;
        private int quantity;
        private double volumeM3;
        private double weightKg;
        private String reason;
    }
}
