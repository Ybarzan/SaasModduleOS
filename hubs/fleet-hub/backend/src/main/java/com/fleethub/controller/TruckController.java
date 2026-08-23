package com.fleethub.controller;

import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.dto.TruckDto;
import com.fleethub.dto.TruckRequest;
import com.fleethub.model.DriverTruckAssignment;
import com.fleethub.model.Truck;
import com.fleethub.repository.AssignmentRepository;
import com.fleethub.repository.CostRecordRepository;
import com.fleethub.repository.DrivingEventRepository;
import com.fleethub.repository.FuelRecordRepository;
import com.fleethub.repository.MaintenanceRepository;
import com.fleethub.repository.TripRepository;
import com.fleethub.repository.TruckRepository;
import com.fleethub.security.TenantContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/trucks")
@RequiredArgsConstructor
@Tag(name = "Camions", description = "Gestion des camions de la flotte")
public class TruckController {

    private final TruckRepository truckRepository;
    private final AssignmentRepository assignmentRepository;
    private final TripRepository tripRepository;
    private final DrivingEventRepository eventRepository;
    private final FuelRecordRepository fuelRecordRepository;
    private final MaintenanceRepository maintenanceRepository;
    private final CostRecordRepository costRecordRepository;

    @GetMapping
    @Operation(summary = "Lister les camions", description = "Retourne la liste de tous les camions avec leur affectation actuelle")
    @ApiResponse(responseCode = "200", description = "Liste retournée avec succès")
    public List<TruckDto> allTrucks() {
        Long companyId = TenantContext.companyId();
        List<DriverTruckAssignment> active = assignmentRepository.findByActiveTrue(companyId);
        return truckRepository.findByCompanyId(companyId).stream().map(t -> {
            DriverTruckAssignment a = active.stream()
                    .filter(x -> x.getTruck().getId().equals(t.getId())).findFirst().orElse(null);
            return toDto(t, a);
        }).toList();
    }

    @PostMapping
    @Transactional
    @Operation(summary = "Créer un camion", description = "Ajoute un nouveau camion à la flotte")
    @ApiResponse(responseCode = "200", description = "Camion créé avec succès")
    @ApiResponse(responseCode = "409", description = "Immatriculation déjà utilisée")
    @ApiResponse(responseCode = "403", description = "Limite de véhicules atteinte pour la formule actuelle")
    public TruckDto create(@Valid @RequestBody TruckRequest req) {
        Long companyId = TenantContext.companyId();
        if (truckRepository.findByRegistrationAndCompanyId(req.registration(), companyId).isPresent()) {
            throw new IllegalArgumentException("Cette immatriculation est déjà utilisée");
        }
        Integer maxVehicles = TenantContext.require().getPlan().getMaxVehicles();
        if (maxVehicles != null && truckRepository.countByCompanyId(companyId) >= maxVehicles) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Limite de véhicules atteinte pour votre formule ("
                            + TenantContext.require().getPlan().name() + "). Contactez le support pour évoluer.");
        }
        Truck t = new Truck();
        t.setCompany(TenantContext.require());
        apply(t, req);
        return toDto(truckRepository.save(t), null);
    }

    @PutMapping("/{id}")
    @Transactional
    @Operation(summary = "Modifier un camion", description = "Met à jour les informations d'un camion existant")
    @ApiResponse(responseCode = "200", description = "Camion mis à jour avec succès")
    @ApiResponse(responseCode = "404", description = "Camion introuvable")
    @ApiResponse(responseCode = "409", description = "Immatriculation déjà utilisée par un autre camion")
    public TruckDto update(@PathVariable Long id, @Valid @RequestBody TruckRequest req) {
        Long companyId = TenantContext.companyId();
        Truck t = truckRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Camion introuvable"));
        truckRepository.findByRegistrationAndCompanyId(req.registration(), companyId)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Cette immatriculation est déjà utilisée");
                });
        apply(t, req);
        return toDto(truckRepository.save(t), null);
    }

    @DeleteMapping("/{id}")
    @Transactional
    @Operation(summary = "Supprimer un camion", description = "Supprime un camion et toutes ses données associées")
    @ApiResponse(responseCode = "204", description = "Camion supprimé avec succès")
    @ApiResponse(responseCode = "404", description = "Camion introuvable")
    public void delete(@PathVariable Long id) {
        Long companyId = TenantContext.companyId();
        Truck t = truckRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Camion introuvable"));
        assignmentRepository.deleteByTruck(t);
        tripRepository.deleteByTruck(t);
        eventRepository.deleteByTruck(t);
        fuelRecordRepository.deleteByTruck(t);
        maintenanceRepository.deleteByTruck(t);
        costRecordRepository.deleteByTruck(t);
        truckRepository.delete(t);
    }

    private void apply(Truck t, TruckRequest req) {
        t.setRegistration(req.registration());
        t.setBrand(req.brand());
        t.setModel(req.model());
        t.setModelYear(req.modelYear());
        t.setTruckType(Truck.TruckType.valueOf(req.truckType()));
        t.setFuelType(Truck.FuelType.valueOf(req.fuelType()));
        t.setCapacityTons(req.capacityTons());
        t.setAcquisitionDate(req.acquisitionDate());
        t.setPurchasePrice(req.purchasePrice());
        t.setExpectedConsumptionL100Km(req.expectedConsumptionL100Km());
        t.setActive(req.active());
    }

    private TruckDto toDto(Truck t, DriverTruckAssignment a) {
        return new TruckDto(
                t.getId(), t.getRegistration(), t.getBrand(), t.getModel(), t.getModelYear(),
                t.getTruckType().name(), t.getFuelType().name(), t.getCapacityTons(),
                t.getAcquisitionDate(), t.getPurchasePrice(), t.getExpectedConsumptionL100Km(),
                t.getCurrentStatus() != null ? t.getCurrentStatus().name() : "ARRET",
                t.getLastGpsUpdate(), t.isActive(),
                a != null ? a.getId() : null,
                a != null ? a.getDriver().getId() : null,
                a != null ? a.getDriver().getFirstName() + " " + a.getDriver().getLastName() : null);
    }
}
