package com.fleethub.service;

import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.dto.CarrierDTO;
import com.fleethub.model.Carrier;
import com.fleethub.model.Company;
import com.fleethub.repository.CarrierRepository;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.repository.ShipmentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CarrierService {

    private final CarrierRepository carrierRepo;
    private final CompanyRepository companyRepo;
    private final ShipmentOrderRepository shipmentOrderRepo;

    public List<Carrier> listCarriers(Long companyId) {
        return carrierRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    public Page<Carrier> listCarriers(Long companyId, Pageable pageable) {
        return carrierRepo.findByCompanyIdOrderByCreatedAtDesc(companyId, pageable);
    }

    @Transactional
    public Carrier createCarrier(CarrierDTO dto, Long companyId) {
        if (carrierRepo.existsByCompanyIdAndCodeIgnoreCase(companyId, dto.getCode())) {
            throw new IllegalArgumentException("Un transporteur avec le code '" + dto.getCode() + "' existe déjà dans votre entreprise");
        }

        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée"));

        Carrier carrier = Carrier.builder()
                .company(company)
                .name(dto.getName())
                .code(dto.getCode())
                .logoUrl(dto.getLogoUrl())
                .transportModes(dto.getTransportModes())
                .apiEndpoint(dto.getApiEndpoint())
                .contactName(dto.getContactName())
                .contactEmail(dto.getContactEmail())
                .contactPhone(dto.getContactPhone())
                .country(dto.getCountry())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .build();

        return carrierRepo.save(carrier);
    }

    @Transactional
    public Carrier updateCarrier(UUID id, CarrierDTO dto, Long companyId) {
        Carrier carrier = carrierRepo.findById(id)
                .filter(c -> c.getCompany() != null && c.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Transporteur non trouvé"));

        carrier.setName(dto.getName());
        if (dto.getCode() != null && !dto.getCode().equals(carrier.getCode())) {
            if (carrierRepo.existsByCompanyIdAndCodeIgnoreCase(companyId, dto.getCode())) {
                throw new IllegalArgumentException("Un transporteur avec le code '" + dto.getCode() + "' existe déjà dans votre entreprise");
            }
            carrier.setCode(dto.getCode());
        }
        if (dto.getLogoUrl() != null) carrier.setLogoUrl(dto.getLogoUrl());
        if (dto.getTransportModes() != null) carrier.setTransportModes(dto.getTransportModes());
        if (dto.getApiEndpoint() != null) carrier.setApiEndpoint(dto.getApiEndpoint());
        if (dto.getContactName() != null) carrier.setContactName(dto.getContactName());
        if (dto.getContactEmail() != null) carrier.setContactEmail(dto.getContactEmail());
        if (dto.getContactPhone() != null) carrier.setContactPhone(dto.getContactPhone());
        if (dto.getCountry() != null) carrier.setCountry(dto.getCountry());
        if (dto.getIsActive() != null) carrier.setActive(dto.getIsActive());

        return carrierRepo.save(carrier);
    }

    @Transactional
    public void deleteCarrier(UUID id, Long companyId) {
        Carrier carrier = carrierRepo.findById(id)
                .filter(c -> c.getCompany() != null && c.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Transporteur non trouvé"));

        long shipmentCount = shipmentOrderRepo.countByCarrier_Id(id);
        if (shipmentCount > 0) {
            throw new IllegalStateException(
                "Impossible de supprimer ce transporteur : " + shipmentCount
                    + " expédition(s) y sont rattachées. Désactivez-le plutôt.");
        }

        carrierRepo.delete(carrier);
    }

    @Transactional
    public Carrier toggleActive(UUID id, Long companyId) {
        Carrier carrier = carrierRepo.findById(id)
                .filter(c -> c.getCompany() != null && c.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Transporteur non trouvé"));
        carrier.setActive(!carrier.isActive());
        return carrierRepo.save(carrier);
    }
}
