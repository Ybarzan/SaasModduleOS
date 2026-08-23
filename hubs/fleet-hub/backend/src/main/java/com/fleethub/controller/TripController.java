package com.fleethub.controller;

import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.dto.TripDto;
import com.fleethub.dto.TripRequest;
import com.fleethub.model.Driver;
import com.fleethub.model.Trip;
import com.fleethub.model.Truck;
import com.fleethub.repository.DriverRepository;
import com.fleethub.repository.TripRepository;
import com.fleethub.repository.TruckRepository;
import com.fleethub.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
@Tag(name = "Trajets", description = "Gestion des trajets de la flotte")
public class TripController {

    private final TripRepository tripRepository;
    private final DriverRepository driverRepository;
    private final TruckRepository truckRepository;

    @GetMapping
    @Operation(summary = "Lister les trajets", description = "Retourne les trajets avec filtres optionnels (chauffeur, camion, période)")
    public List<TripDto> all(@RequestParam(required = false) Long driverId,
                             @RequestParam(required = false) Long truckId,
                             @RequestParam(required = false) String from,
                             @RequestParam(required = false) String to) {
        LocalDateTime fromTs = from != null ? LocalDateTime.parse(from) : null;
        LocalDateTime toTs = to != null ? LocalDateTime.parse(to) : null;
        return tripRepository.findAllFetch(TenantContext.companyId()).stream()
                .filter(t -> driverId == null || t.getDriver().getId().equals(driverId))
                .filter(t -> truckId == null || t.getTruck().getId().equals(truckId))
                .filter(t -> fromTs == null || !t.getStartTime().isBefore(fromTs))
                .filter(t -> toTs == null || !t.getStartTime().isAfter(toTs))
                .map(this::toDto)
                .toList();
    }

    @PostMapping
    @Operation(summary = "Créer un trajet", description = "Enregistre un nouveau trajet de la flotte")
    @Transactional
    public TripDto create(@Valid @RequestBody TripRequest req) {
        Long companyId = TenantContext.companyId();
        Driver driver = driverRepository.findByIdAndCompanyId(req.driverId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Chauffeur introuvable"));
        Truck truck = truckRepository.findByIdAndCompanyId(req.truckId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Camion introuvable"));
        Trip t = new Trip();
        t.setCompany(TenantContext.require());
        apply(t, req, driver, truck);
        return toDto(tripRepository.save(t));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un trajet", description = "Met à jour les informations d'un trajet existant")
    @Transactional
    public TripDto update(@PathVariable Long id, @Valid @RequestBody TripRequest req) {
        Long companyId = TenantContext.companyId();
        Trip t = tripRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Trajet introuvable"));
        Driver driver = driverRepository.findByIdAndCompanyId(req.driverId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Chauffeur introuvable"));
        Truck truck = truckRepository.findByIdAndCompanyId(req.truckId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Camion introuvable"));
        apply(t, req, driver, truck);
        return toDto(tripRepository.save(t));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un trajet", description = "Supprime un trajet de la flotte")
    @Transactional
    public void delete(@PathVariable Long id) {
        Trip t = tripRepository.findByIdAndCompanyId(id, TenantContext.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Trajet introuvable"));
        tripRepository.delete(t);
    }

    private void apply(Trip t, TripRequest req, Driver driver, Truck truck) {
        t.setDriver(driver);
        t.setTruck(truck);
        t.setStartTime(req.startTime());
        t.setEndTime(req.endTime());
        t.setDistanceKm(req.distanceKm());
        t.setCargoWeightTons(req.cargoWeightTons());
        t.setLoaded(req.loaded());
        t.setStatus(Trip.TripStatus.valueOf(req.status()));
        t.setOnTime(req.onTime());
    }

    private TripDto toDto(Trip t) {
        return new TripDto(
                t.getId(),
                t.getDriver().getId(),
                t.getDriver().getFirstName() + " " + t.getDriver().getLastName(),
                t.getTruck().getId(),
                t.getTruck().getRegistration(),
                t.getStartTime(), t.getEndTime(),
                t.getDistanceKm(), t.getCargoWeightTons(),
                t.isLoaded(), t.getStatus().name(), t.isOnTime());
    }
}
