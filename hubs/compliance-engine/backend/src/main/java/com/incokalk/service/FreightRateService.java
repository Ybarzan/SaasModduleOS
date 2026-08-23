package com.incokalk.service;

import com.incokalk.dto.shipment.SimulationRequest.TransportModeInput;
import com.incokalk.model.ShipmentOrder.ContainerType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class FreightRateService {

    public record FreightEstimate(double cost, int days) {}

    private static final Map<String, Map<String, Double>> SEA_FCL_20FT = Map.of(
        "CN", Map.of("FR",2800.0,"DE",2750.0,"NL",2700.0,"ES",2900.0,"IT",2950.0),
        "VN", Map.of("FR",3200.0,"DE",3100.0,"NL",3050.0),
        "IN", Map.of("FR",1200.0,"DE",1150.0,"NL",1100.0),
        "MA", Map.of("FR",450.0,"DE",520.0,"ES",350.0,"IT",480.0),
        "TR", Map.of("FR",680.0,"DE",620.0,"IT",590.0)
    );

    private static final Map<ContainerType, Double> CONTAINER_MULTIPLIERS = Map.ofEntries(
        Map.entry(ContainerType.DRY_20FT, 1.0),
        Map.entry(ContainerType.DRY_40FT, 1.85),
        Map.entry(ContainerType.REEFER_20FT, 2.05),
        Map.entry(ContainerType.REEFER_40FT, 3.6),
        Map.entry(ContainerType.TANDEM_40FT, 3.1),
        Map.entry(ContainerType.FLAT_RAIL, 0.75),
        Map.entry(ContainerType.OPEN_TOP_DRY, 1.2),
        Map.entry(ContainerType.OPEN_TOP_REEFER, 2.8),
        Map.entry(ContainerType.FRIGO_40FT, 3.4),
        Map.entry(ContainerType.ISO_45, 1.3),
        Map.entry(ContainerType.CUSTOM, 1.0)
    );

    private static final Map<ContainerType, Double> CONTAINER_VOLUMES = Map.ofEntries(
        Map.entry(ContainerType.DRY_20FT, 33.0),
        Map.entry(ContainerType.DRY_40FT, 76.0),
        Map.entry(ContainerType.REEFER_20FT, 33.0),
        Map.entry(ContainerType.REEFER_40FT, 76.0),
        Map.entry(ContainerType.TANDEM_40FT, 120.0),
        Map.entry(ContainerType.FLAT_RAIL, 50.0),
        Map.entry(ContainerType.OPEN_TOP_DRY, 70.0),
        Map.entry(ContainerType.OPEN_TOP_REEFER, 70.0),
        Map.entry(ContainerType.FRIGO_40FT, 76.0),
        Map.entry(ContainerType.ISO_45, 45.0),
        Map.entry(ContainerType.CUSTOM, 50.0)
    );

    private static final Set<String> NEARSHORE = Set.of("MA","TN","DZ","TR","PL","RO","HU","BG");
    private static final Set<String> EUROPE    = Set.of("FR","DE","NL","BE","IT","ES","PT","AT","CH","LU","DK",
        "IE","GB","PL","CZ","SK","HU","SI","HR","BG","RO","SE","FI","NO","EE","LV","LT","GR","RS","UA");
    private static final Set<String> FAR_EAST  = Set.of("CN","JP","KR","VN","TH","ID","MY","PH");

    public FreightEstimate estimate(String origin, String dest, TransportModeInput mode,
                            Double weightKg, Double volumeM3, double goodsValue) {
        if (mode == null) mode = guess(origin, goodsValue);
        return switch (mode) {
            case SEA        -> sea(origin, dest, weightKg, volumeM3, goodsValue);
            case AIR        -> air(origin, weightKg, volumeM3, goodsValue);
            case ROAD       -> road(origin, dest, weightKg, goodsValue);
            case MULTIMODAL -> {
                FreightEstimate seaPart = sea(origin,"NL",weightKg,volumeM3,goodsValue);
                FreightEstimate roadPart = road("NL",dest,weightKg,goodsValue);
                yield new FreightEstimate(
                    seaPart.cost() * 0.8 + roadPart.cost() * 1.2 + 180,
                    Math.max(seaPart.days(), roadPart.days()) + 5
                );
            }
        };
    }

    private FreightEstimate sea(String orig, String dest, Double w, Double v, double val) {
        double cost;
        if (v != null && v >= 25) {
            double fcl = SEA_FCL_20FT.getOrDefault(orig.toUpperCase(), Map.of())
                .getOrDefault(dest.toUpperCase(), FAR_EAST.contains(orig.toUpperCase()) ? 2800.0 : 900.0);
            cost = fcl * Math.ceil(v / 25.0) * 1.18;
        } else {
            double cbm = v != null ? v : (w != null ? w/1000.0 : val/8000.0);
            cost = Math.max(cbm, 1.0) * 65 * 1.18;
        }
        int days = FAR_EAST.contains(orig.toUpperCase()) ? 35 : 15;
        return new FreightEstimate(Math.round(cost * 100.0) / 100.0, days);
    }

    private FreightEstimate air(String orig, Double w, Double v, double val) {
        double cw = (w != null && v != null) ? Math.max(w, v*1000000/6000.0) : (w != null ? w : val*0.15);
        double bw = Math.max(cw, 45);
        double rate = FAR_EAST.contains(orig.toUpperCase()) ? 4.80 : (NEARSHORE.contains(orig.toUpperCase()) ? 1.80 : 3.80);
        double cost = bw * (rate + 0.80 + 0.15);
        int days = FAR_EAST.contains(orig.toUpperCase()) ? 7 : 3;
        return new FreightEstimate(Math.round(cost * 100.0) / 100.0, days);
    }

    private FreightEstimate road(String orig, String dest, Double w, double val) {
        double pallets = w != null ? Math.ceil(w/800.0) : Math.ceil(val/5000.0);
        if (pallets >= 33) return new FreightEstimate(1800.0, 14);
        double rate = NEARSHORE.contains(orig.toUpperCase()) ? 95.0 : 150.0;
        double cost = pallets * rate;
        int days;
        if (EUROPE.contains(orig.toUpperCase()) && EUROPE.contains(dest.toUpperCase())) {
            days = 2;
        } else if (NEARSHORE.contains(orig.toUpperCase())) {
            days = 7;
        } else {
            days = 12;
        }
        return new FreightEstimate(Math.round(cost * 100.0) / 100.0, days);
    }

    public TransportModeInput guess(String orig, double val) {
        if (NEARSHORE.contains(orig.toUpperCase())) return TransportModeInput.ROAD;
        if (val > 50000) return TransportModeInput.AIR;
        return TransportModeInput.SEA;
    }
}