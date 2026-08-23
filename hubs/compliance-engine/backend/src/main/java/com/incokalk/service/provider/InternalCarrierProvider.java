package com.incokalk.service.provider;

import com.incokalk.dto.config.ProviderHealthDTO;
import com.incokalk.dto.shipment.QuoteRequestDTO;
import com.incokalk.dto.shipment.QuoteResponseDTO;
import com.incokalk.model.Carrier;
import com.incokalk.model.ShippingRate;
import com.incokalk.repository.CarrierRepository;
import com.incokalk.repository.ShippingRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class InternalCarrierProvider implements CarrierProvider {

    private final ShippingRateRepository shippingRateRepo;
    private final CarrierRepository carrierRepo;

    @Override
    public String getProviderType() {
        return "INTERNAL";
    }

    @Override
    public String getName() {
        return "Tarifs internes";
    }

    @Override
    public List<QuoteResponseDTO> getRates(QuoteRequestDTO request, UUID companyId) {
        log.debug("[INTERNAL] Récupération des tarifs internes pour companyId={}", companyId);

        List<ShippingRate> rates;

        if (request.getTransportMode() != null && !request.getTransportMode().isBlank()) {
            rates = shippingRateRepo.findByCompany_IdAndOriginCountryAndDestinationCountryAndTransportModeAndIsActiveTrue(
                    companyId, request.getOriginCountry(), request.getDestinationCountry(), request.getTransportMode());
        } else {
            rates = shippingRateRepo.findByCompany_IdAndOriginCountryAndDestinationCountry(
                    companyId, request.getOriginCountry(), request.getDestinationCountry());
        }

        List<UUID> carrierIds = rates.stream()
                .map(r -> r.getCarrier().getId())
                .distinct()
                .toList();

        Map<UUID, Carrier> carrierMap = carrierRepo.findAllById(carrierIds).stream()
                .collect(Collectors.toMap(Carrier::getId, c -> c));

        return rates.stream()
                .filter(rate -> {
                    Carrier carrier = carrierMap.get(rate.getCarrier().getId());
                    return carrier != null && carrier.isActive();
                })
                .filter(rate -> {
                    if (rate.getMinWeightKg() != null && request.getWeightKg() < rate.getMinWeightKg()) return false;
                    if (rate.getMaxWeightKg() != null && request.getWeightKg() > rate.getMaxWeightKg()) return false;
                    return true;
                })
                .map(rate -> {
                    Carrier carrier = carrierMap.get(rate.getCarrier().getId());
                    double totalCost = rate.getBaseRate()
                            + (request.getWeightKg() * rate.getRatePerKg())
                            + (request.getVolumeM3() * rate.getRatePerCbm());

                    return QuoteResponseDTO.builder()
                            .rateId(rate.getId())
                            .carrierId(carrier.getId())
                            .carrierName(carrier.getName())
                            .carrierLogo(carrier.getLogoUrl())
                            .rateName(rate.getName())
                            .transportMode(rate.getTransportMode())
                            .baseRate(rate.getBaseRate())
                            .totalCost(Math.round(totalCost * 100.0) / 100.0)
                            .currency(rate.getCurrency())
                            .transitDaysMin(rate.getTransitDaysMin())
                            .transitDaysMax(rate.getTransitDaysMax())
                            .co2EstimateKg(rate.getCo2EstimateKg())
                            .providerType(getProviderType())
                            .providerName(getName())
                            .providerLogo(null)
                            .build();
                })
                .sorted(Comparator.comparingDouble(QuoteResponseDTO::getTotalCost))
                .toList();
    }

    @Override
    public boolean isAvailable(UUID companyId) {
        return true;
    }

    @Override
    public ProviderHealthDTO getHealth(UUID companyId) {
        return ProviderHealthDTO.builder()
                .providerType(getProviderType())
                .healthStatus("HEALTHY")
                .lastHealthCheck(LocalDateTime.now())
                .consecutiveFailures(0)
                .isActive(true)
                .build();
    }
}
