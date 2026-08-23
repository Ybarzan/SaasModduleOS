package com.incokalk.service;

import com.incokalk.dto.shipment.ShippingRateDTO;
import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Carrier;
import com.incokalk.model.Company;
import com.incokalk.model.ShippingRate;
import com.incokalk.repository.CarrierRepository;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.ShippingRateRepository;
import com.incokalk.tenant.TenantContext;
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

    public List<ShippingRate> listRates(UUID companyId) {
        return shippingRateRepo.findByCompany_IdOrderByCreatedAtDesc(companyId);
    }

    public Page<ShippingRate> listRates(UUID companyId, Pageable pageable) {
        return shippingRateRepo.findByCompany_IdOrderByCreatedAtDesc(companyId, pageable);
    }

    public List<ShippingRate> listRatesByCarrier(UUID carrierId, UUID companyId) {
        return shippingRateRepo.findByCarrier_IdAndCompany_IdOrderByCreatedAtDesc(carrierId, companyId);
    }

    @Transactional
    public ShippingRate createRate(ShippingRateDTO dto, UUID companyId) {
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
    public ShippingRate updateRate(UUID id, ShippingRateDTO dto, UUID companyId) {
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
    public void deleteRate(UUID id, UUID companyId) {
        ShippingRate rate = shippingRateRepo.findById(id)
                .filter(r -> r.getCompany() != null && r.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Tarif non trouvé"));
        shippingRateRepo.delete(rate);
    }

    public List<ShippingRate> findMatchingRates(UUID companyId, String originCountry, String destinationCountry,
                                                  String transportMode, Double weightKg, LocalDateTime date) {
        return shippingRateRepo.findMatchingRates(companyId, originCountry, destinationCountry,
                transportMode, weightKg, date);
    }

    @Transactional
    public ShippingRate toggleActive(UUID id, UUID companyId) {
        ShippingRate rate = shippingRateRepo.findById(id)
                .filter(r -> r.getCompany() != null && r.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Tarif non trouvé"));
        rate.setActive(!rate.isActive());
        return shippingRateRepo.save(rate);
    }
}