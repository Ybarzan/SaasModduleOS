package com.fleethub.service;

import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.dto.ShippingRateDTO;
import com.fleethub.model.Carrier;
import com.fleethub.model.Company;
import com.fleethub.model.ShippingRate;
import com.fleethub.repository.CarrierRepository;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.repository.ShippingRateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShippingRateService {

    private final ShippingRateRepository shippingRateRepo;
    private final CarrierRepository carrierRepo;
    private final CompanyRepository companyRepo;

    public List<ShippingRate> listRates(Long companyId) {
        return shippingRateRepo.findByCompany_IdOrderByCreatedAtDesc(companyId);
    }

    public Page<ShippingRate> listRates(Long companyId, Pageable pageable) {
        return shippingRateRepo.findByCompany_IdOrderByCreatedAtDesc(companyId, pageable);
    }

    public List<ShippingRate> listRatesByCarrier(UUID carrierId, Long companyId) {
        return shippingRateRepo.findByCarrier_IdAndCompany_IdOrderByCreatedAtDesc(carrierId, companyId);
    }

    @Transactional
    public ShippingRate createRate(ShippingRateDTO dto, Long companyId) {
        Carrier carrier = carrierRepo.findById(dto.getCarrierId())
                .filter(c -> c.getCompany() != null && c.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Transporteur non trouvé"));

        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée"));

        ShippingRate rate = ShippingRate.builder()
                .carrier(carrier)
                .company(company)
                .name(dto.getName())
                .originCountry(dto.getOriginCountry())
                .destinationCountry(dto.getDestinationCountry())
                .transportMode(dto.getTransportMode())
                .minWeightKg(dto.getMinWeightKg())
                .maxWeightKg(dto.getMaxWeightKg())
                .baseRate(dto.getBaseRate())
                .currency(dto.getCurrency() != null ? dto.getCurrency() : "EUR")
                .ratePerKg(dto.getRatePerKg() != null ? dto.getRatePerKg() : 0)
                .ratePerCbm(dto.getRatePerCbm() != null ? dto.getRatePerCbm() : 0)
                .transitDaysMin(dto.getTransitDaysMin())
                .transitDaysMax(dto.getTransitDaysMax())
                .co2EstimateKg(dto.getCo2EstimateKg())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .validFrom(dto.getValidFrom())
                .validUntil(dto.getValidUntil())
                .build();

        return shippingRateRepo.save(rate);
    }

    @Transactional
    public ShippingRate updateRate(UUID id, ShippingRateDTO dto, Long companyId) {
        ShippingRate rate = shippingRateRepo.findById(id)
                .filter(r -> r.getCompany() != null && r.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Tarif non trouvé"));

        if (dto.getName() != null) rate.setName(dto.getName());
        if (dto.getOriginCountry() != null) rate.setOriginCountry(dto.getOriginCountry());
        if (dto.getDestinationCountry() != null) rate.setDestinationCountry(dto.getDestinationCountry());
        if (dto.getTransportMode() != null) rate.setTransportMode(dto.getTransportMode());
        if (dto.getMinWeightKg() != null) rate.setMinWeightKg(dto.getMinWeightKg());
        if (dto.getMaxWeightKg() != null) rate.setMaxWeightKg(dto.getMaxWeightKg());
        if (dto.getBaseRate() != 0) rate.setBaseRate(dto.getBaseRate());
        if (dto.getCurrency() != null) rate.setCurrency(dto.getCurrency());
        if (dto.getRatePerKg() != null) rate.setRatePerKg(dto.getRatePerKg());
        if (dto.getRatePerCbm() != null) rate.setRatePerCbm(dto.getRatePerCbm());
        if (dto.getTransitDaysMin() != null) rate.setTransitDaysMin(dto.getTransitDaysMin());
        if (dto.getTransitDaysMax() != null) rate.setTransitDaysMax(dto.getTransitDaysMax());
        if (dto.getCo2EstimateKg() != null) rate.setCo2EstimateKg(dto.getCo2EstimateKg());
        if (dto.getIsActive() != null) rate.setActive(dto.getIsActive());
        if (dto.getValidFrom() != null) rate.setValidFrom(dto.getValidFrom());
        if (dto.getValidUntil() != null) rate.setValidUntil(dto.getValidUntil());

        return shippingRateRepo.save(rate);
    }

    @Transactional
    public void deleteRate(UUID id, Long companyId) {
        ShippingRate rate = shippingRateRepo.findById(id)
                .filter(r -> r.getCompany() != null && r.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Tarif non trouvé"));
        shippingRateRepo.delete(rate);
    }

    public List<ShippingRate> findMatchingRates(Long companyId, String originCountry, String destinationCountry,
                                                  String transportMode, Double weightKg, LocalDateTime date) {
        return shippingRateRepo.findMatchingRates(companyId, originCountry, destinationCountry,
                transportMode, weightKg, date);
    }

    @Transactional
    public ShippingRate toggleActive(UUID id, Long companyId) {
        ShippingRate rate = shippingRateRepo.findById(id)
                .filter(r -> r.getCompany() != null && r.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Tarif non trouvé"));
        rate.setActive(!rate.isActive());
        return shippingRateRepo.save(rate);
    }
}
