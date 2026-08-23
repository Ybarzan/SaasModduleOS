package com.fleethub.controller;

import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.dto.AssignmentDto;
import com.fleethub.dto.AssignmentRequest;
import com.fleethub.model.Driver;
import com.fleethub.model.DriverTruckAssignment;
import com.fleethub.model.Truck;
import com.fleethub.repository.AssignmentRepository;
import com.fleethub.repository.DriverRepository;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
@Tag(name = "Affectations", description = "Affectation chauffeurs-camions de la flotte")
public class AssignmentController {

    private final AssignmentRepository assignmentRepository;
    private final DriverRepository driverRepository;
    private final TruckRepository truckRepository;

    @GetMapping
    @Operation(summary = "Lister les affectations", description = "Retourne toutes les affectations chauffeur-camion actives et historiques")
    public List<AssignmentDto> all() {
        return assignmentRepository.findAllFetch(TenantContext.companyId()).stream().map(this::toDto).toList();
    }

    @PostMapping
    @Operation(summary = "Créer une affectation", description = "Affecte un chauffeur à un camion. Si active=true, les affectations conflictuelles sont automatiquement désactivées.")
    @Transactional
    public AssignmentDto create(@Valid @RequestBody AssignmentRequest req) {
        Long companyId = TenantContext.companyId();
        Driver driver = driverRepository.findByIdAndCompanyId(req.driverId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Chauffeur introuvable"));
        Truck truck = truckRepository.findByIdAndCompanyId(req.truckId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Camion introuvable"));
        if (req.active()) releaseConflicts(req.driverId(), req.truckId(), null);
        DriverTruckAssignment a = new DriverTruckAssignment(null, TenantContext.require(),
                driver, truck, req.startDate(), req.endDate(), req.active());
        return toDto(assignmentRepository.save(a));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier une affectation", description = "Met à jour les dates et le statut d'une affectation")
    @Transactional
    public AssignmentDto update(@PathVariable Long id, @Valid @RequestBody AssignmentRequest req) {
        Long companyId = TenantContext.companyId();
        DriverTruckAssignment a = assignmentRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Affectation introuvable"));
        Driver driver = driverRepository.findByIdAndCompanyId(req.driverId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Chauffeur introuvable"));
        Truck truck = truckRepository.findByIdAndCompanyId(req.truckId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Camion introuvable"));
        if (req.active()) releaseConflicts(req.driverId(), req.truckId(), id);
        a.setDriver(driver);
        a.setTruck(truck);
        a.setStartDate(req.startDate());
        a.setEndDate(req.endDate());
        a.setActive(req.active());
        return toDto(assignmentRepository.save(a));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer une affectation", description = "Supprime une affectation chauffeur-camion")
    @Transactional
    public void delete(@PathVariable Long id) {
        DriverTruckAssignment a = assignmentRepository.findByIdAndCompanyId(id, TenantContext.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Affectation introuvable"));
        assignmentRepository.delete(a);
    }

    private void releaseConflicts(Long driverId, Long truckId, Long excludedId) {
        assignmentRepository.findAllFetch(TenantContext.companyId()).stream()
                .filter(a -> a.isActive())
                .filter(a -> !a.getId().equals(excludedId))
                .filter(a -> a.getDriver().getId().equals(driverId) || a.getTruck().getId().equals(truckId))
                .forEach(a -> {
                    a.setActive(false);
                    assignmentRepository.save(a);
                });
    }

    private AssignmentDto toDto(DriverTruckAssignment a) {
        return new AssignmentDto(
                a.getId(),
                a.getDriver().getId(),
                a.getDriver().getFirstName() + " " + a.getDriver().getLastName(),
                a.getTruck().getId(),
                a.getTruck().getRegistration(),
                a.getStartDate(), a.getEndDate(), a.isActive());
    }
}
