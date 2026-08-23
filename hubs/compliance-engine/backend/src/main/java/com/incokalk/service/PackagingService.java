package com.incokalk.service;

import com.incokalk.dto.shipment.PackagingRequest;
import com.incokalk.dto.shipment.PackagingResult;
import com.incokalk.dto.shipment.PackagingResult.BoxInfo;
import com.incokalk.dto.shipment.PackagingResult.PackedItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
public class PackagingService {

    private static final List<DefaultBox> DEFAULT_BOXES = List.of(
        new DefaultBox("BOX-XS", 30, 20, 15, 5),
        new DefaultBox("BOX-S", 40, 30, 25, 10),
        new DefaultBox("BOX-M", 50, 40, 30, 20),
        new DefaultBox("BOX-L", 60, 50, 40, 30),
        new DefaultBox("BOX-XL", 80, 60, 50, 50),
        new DefaultBox("BOX-XXL", 100, 80, 60, 100),
        new DefaultBox("BOX-PALLET", 120, 80, 100, 500)
    );

    public PackagingResult calculatePackaging(PackagingRequest request) {
        List<PackagingRequest.PackagingItem> items = request.getItems();
        List<PackagingRequest.AvailableBox> customBoxes = request.getAvailableBoxes();

        List<DefaultBox> boxes = customBoxes != null && !customBoxes.isEmpty()
            ? customBoxes.stream().map(this::toDefaultBox).collect(Collectors.toList())
            : new ArrayList<>(DEFAULT_BOXES);

        boxes.sort(Comparator.comparingDouble(DefaultBox::volume).reversed());

        List<ItemEntry> itemEntries = items.stream()
            .flatMap(item -> {
                double itemVol = toM3(item.getLengthCm(), item.getWidthCm(), item.getHeightCm());
                double itemWeight = item.getWeightKg();
                return Stream.generate(() -> new ItemEntry(
                    item.getSku(),
                    item.getLengthCm(), item.getWidthCm(), item.getHeightCm(),
                    itemVol, itemWeight
                )).limit(item.getQuantity());
            })
            .sorted(Comparator.comparingDouble(ItemEntry::volume).reversed())
            .collect(Collectors.toList());

        List<BoxSlot> boxSlots = new ArrayList<>();
        List<ItemEntry> unpacked = new ArrayList<>();

        for (ItemEntry item : itemEntries) {
            boolean placed = false;
            for (BoxSlot slot : boxSlots) {
                if (slot.canFit(item)) {
                    slot.add(item);
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                DefaultBox bestBox = findBestBox(boxes, item);
                if (bestBox != null) {
                    BoxSlot newSlot = new BoxSlot(bestBox);
                    newSlot.add(item);
                    boxSlots.add(newSlot);
                    placed = true;
                }
            }
            if (!placed) {
                unpacked.add(item);
            }
        }

        double totalVol = boxSlots.stream().mapToDouble(BoxSlot::totalVolumeM3).sum();
        double totalWeight = boxSlots.stream().mapToDouble(BoxSlot::totalWeightKg).sum();
        double usedVol = boxSlots.stream().mapToDouble(BoxSlot::usedVolumeM3).sum();
        double utilization = totalVol > 0 ? (usedVol / totalVol) * 100 : 0;

        List<BoxInfo> boxInfos = boxSlots.stream()
            .map(slot -> {
                Map<String, PackedItem> grouped = slot.items.stream()
                    .collect(Collectors.toMap(
                        i -> i.sku,
                        i -> PackedItem.builder().sku(i.sku).quantity(1).volumeM3(i.volume).weightKg(i.weight).build(),
                        (a, b) -> PackedItem.builder()
                            .sku(a.getSku())
                            .quantity(a.getQuantity() + b.getQuantity())
                            .volumeM3(a.getVolumeM3() + b.getVolumeM3())
                            .weightKg(a.getWeightKg() + b.getWeightKg())
                            .build()
                    ));
                return BoxInfo.builder()
                    .boxRef(slot.box.ref)
                    .lengthCm(slot.box.lengthCm)
                    .widthCm(slot.box.widthCm)
                    .heightCm(slot.box.heightCm)
                    .volumeM3(slot.box.volume())
                    .usedVolumeM3(slot.usedVolumeM3())
                    .utilizationPercent(slot.utilizationPercent())
                    .totalWeightKg(slot.totalWeightKg())
                    .packedItems(new ArrayList<>(grouped.values()))
                    .build();
            })
            .collect(Collectors.toList());

        List<PackagingResult.ItemInfo> unpackedInfos = unpacked.stream()
            .collect(Collectors.groupingBy(i -> i.sku, Collectors.toList()))
            .entrySet().stream()
            .map(e -> {
                List<ItemEntry> entries = e.getValue();
                return PackagingResult.ItemInfo.builder()
                    .sku(e.getKey())
                    .quantity(entries.size())
                    .volumeM3(entries.stream().mapToDouble(i -> i.volume).sum())
                    .weightKg(entries.stream().mapToDouble(i -> i.weight).sum())
                    .reason("Aucune boîte disponible ne peut contenir cet article")
                    .build();
            })
            .collect(Collectors.toList());

        return PackagingResult.builder()
            .totalBoxes(boxSlots.size())
            .totalVolumeM3(totalVol)
            .totalWeightKg(totalWeight)
            .utilizationPercent(Math.round(utilization * 100.0) / 100.0)
            .totalPackageVolumeM3(usedVol)
            .boxes(boxInfos)
            .unpackedItems(unpackedInfos)
            .build();
    }

    private DefaultBox findBestBox(List<DefaultBox> boxes, ItemEntry item) {
        return boxes.stream()
            .filter(b -> b.lengthCm >= item.length && b.widthCm >= item.width && b.heightCm >= item.height)
            .filter(b -> b.maxWeightKg >= item.weight)
            .findFirst()
            .orElse(null);
    }

    private DefaultBox toDefaultBox(PackagingRequest.AvailableBox b) {
        return new DefaultBox(b.getRef(), b.getLengthCm(), b.getWidthCm(), b.getHeightCm(), b.getMaxWeightKg());
    }

    private static double toM3(double l, double w, double h) {
        return (l * w * h) / 1_000_000.0;
    }

    record DefaultBox(String ref, double lengthCm, double widthCm, double heightCm, double maxWeightKg) {
        double volume() { return toM3(lengthCm, widthCm, heightCm); }
    }

    record ItemEntry(
        String sku,
        double length, double width, double height,
        double volume, double weight
    ) {}

    static class BoxSlot {
        final DefaultBox box;
        final List<ItemEntry> items = new ArrayList<>();
        double usedVolume = 0;
        double totalWeight = 0;

        BoxSlot(DefaultBox box) {
            this.box = box;
        }

        boolean canFit(ItemEntry item) {
            return box.lengthCm >= item.length && box.widthCm >= item.width && box.heightCm >= item.height
                && (usedVolume + item.volume) <= box.volume() * 0.95
                && (totalWeight + item.weight) <= box.maxWeightKg;
        }

        void add(ItemEntry item) {
            items.add(item);
            usedVolume += item.volume;
            totalWeight += item.weight;
        }

        double totalVolumeM3() { return box.volume(); }
        double usedVolumeM3() { return usedVolume; }
        double totalWeightKg() { return totalWeight; }
        double utilizationPercent() { return box.volume() > 0 ? (usedVolume / box.volume()) * 100 : 0; }
    }
}
