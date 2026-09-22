package com.fleethub.service;

import com.fleethub.dto.MarketplaceAvailabilityDto;
import com.fleethub.dto.MarketplaceSettingsDto;
import com.fleethub.dto.VehiclePositionDto;
import com.fleethub.model.Company;
import com.fleethub.model.Truck;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.repository.TachographDayRepository;
import com.fleethub.repository.TruckRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Publication opt-in de la disponibilité d'une société vers FleetMarket
 * (voir SPEC.md du repo FleetMarket, section "Intégration technique").
 * Jamais d'accès direct entre les deux bases : FleetMarket appelle
 * GET /api/marketplace/availability avec la clé machine-à-machine de
 * chaque société qui a activé le partage.
 */
@Service
@RequiredArgsConstructor
public class MarketplaceService {

    private static final Set<Truck.VehicleStatus> AVAILABLE_STATUSES =
            EnumSet.of(Truck.VehicleStatus.ARRET, Truck.VehicleStatus.REPOS);

    private final CompanyRepository companyRepository;
    private final TruckRepository truckRepository;
    private final TachographDayRepository tachographDayRepository;

    @Transactional(readOnly = true)
    public MarketplaceSettingsDto settings(Long companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        return new MarketplaceSettingsDto(company.isMarketplaceOptIn(), null);
    }

    @Transactional
    public MarketplaceSettingsDto optIn(Long companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        if (company.getMarketplaceApiKey() == null) {
            company.setMarketplaceApiKey(Company.generateMarketplaceApiKey());
        }
        company.setMarketplaceOptIn(true);
        companyRepository.save(company);
        return new MarketplaceSettingsDto(true, company.getMarketplaceApiKey());
    }

    @Transactional
    public void optOut(Long companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        company.setMarketplaceOptIn(false);
        companyRepository.save(company);
    }

    @Transactional(readOnly = true)
    public MarketplaceAvailabilityDto availability(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clé X-Marketplace-Key manquante");
        }
        Company company = companyRepository.findByMarketplaceApiKeyAndMarketplaceOptInTrue(apiKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clé invalide ou partage désactivé"));

        List<MarketplaceAvailabilityDto.AvailableTruckDto> trucks = truckRepository.findByCompanyId(company.getId()).stream()
                .filter(Truck::isActive)
                .filter(t -> t.getCurrentStatus() == null || AVAILABLE_STATUSES.contains(t.getCurrentStatus()))
                .map(t -> new MarketplaceAvailabilityDto.AvailableTruckDto(t.getId(), t.getRegistration(), t.getCapacityTons()))
                .toList();

        Integer complianceScore = complianceScore(company.getId());

        return new MarketplaceAvailabilityDto(company.getName(), company.getCity(), complianceScore, trucks);
    }

    /**
     * Position GPS d'UN SEUL camion, jamais la flotte entière — le camion
     * doit appartenir à la société propriétaire de la clé (même
     * findByRegistrationAndCompanyId que le reste de l'app, pas de nouvelle
     * requête non scopée) : sans ça, une société opt-in pourrait interroger
     * la position de n'importe quel camion d'une autre société en devinant
     * son immatriculation.
     */
    @Transactional(readOnly = true)
    public VehiclePositionDto vehiclePosition(String apiKey, String registration) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clé X-Marketplace-Key manquante");
        }
        Company company = companyRepository.findByMarketplaceApiKeyAndMarketplaceOptInTrue(apiKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clé invalide ou partage désactivé"));
        Truck truck = truckRepository.findByRegistrationAndCompanyId(registration, company.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Camion introuvable pour cette société"));
        if (truck.getCurrentLatitude() == null || truck.getCurrentLongitude() == null) {
            return new VehiclePositionDto(registration, false, null, null, null, null);
        }
        return new VehiclePositionDto(registration, true, truck.getCurrentLatitude(), truck.getCurrentLongitude(),
                truck.getCurrentSpeedKph(), truck.getLastGpsUpdate());
    }

    private Integer complianceScore(Long companyId) {
        LocalDate to = LocalDate.now();
        LocalDate from = to.minusDays(29);
        long total = tachographDayRepository.countByCompanyIdAndDateBetween(companyId, from, to);
        if (total == 0) return null;
        long nonCompliant = tachographDayRepository.countNonCompliantBetween(companyId, from, to);
        return (int) Math.round((total - nonCompliant) * 100.0 / total);
    }
}
