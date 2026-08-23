package com.fleethub.controller;

import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.dto.DriverDto;
import com.fleethub.dto.DriverRequest;
import com.fleethub.model.Driver;
import com.fleethub.model.DriverTruckAssignment;
import com.fleethub.repository.AssignmentRepository;
import com.fleethub.repository.CostRecordRepository;
import com.fleethub.repository.DriverRepository;
import com.fleethub.repository.DrivingEventRepository;
import com.fleethub.repository.TachographDayRepository;
import com.fleethub.repository.TripRepository;
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
@RequestMapping("/api/drivers")
@RequiredArgsConstructor
@Transactional
@Tag(name = "Chauffeurs", description = "Gestion des chauffeurs de la flotte")
public class DriverController {

    private final DriverRepository driverRepository;
    private final AssignmentRepository assignmentRepository;
    private final TripRepository tripRepository;
    private final DrivingEventRepository eventRepository;
    private final TachographDayRepository tachographRepository;
    private final CostRecordRepository costRecordRepository;

    @GetMapping
    @Operation(summary = "Lister les chauffeurs", description = "Retourne la liste de tous les chauffeurs avec leur affectation actuelle")
    @ApiResponse(responseCode = "200", description = "Liste retournée avec succès")
    public List<DriverDto> allDrivers() {
        Long companyId = TenantContext.companyId();
        List<DriverTruckAssignment> active = assignmentRepository.findByActiveTrue(companyId);
        return driverRepository.findByCompanyId(companyId).stream().map(d -> {
            DriverTruckAssignment a = active.stream()
                    .filter(x -> x.getDriver().getId().equals(d.getId())).findFirst().orElse(null);
            return toDto(d, a);
        }).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un chauffeur", description = "Retourne les informations d'un chauffeur par son identifiant")
    @ApiResponse(responseCode = "200", description = "Chauffeur trouvé")
    @ApiResponse(responseCode = "404", description = "Chauffeur introuvable")
    public DriverDto getOne(@PathVariable Long id) {
        Long companyId = TenantContext.companyId();
        Driver d = driverRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Chauffeur introuvable"));
        DriverTruckAssignment a = assignmentRepository.findByActiveTrue(companyId).stream()
                .filter(x -> x.getDriver().getId().equals(d.getId())).findFirst().orElse(null);
        return toDto(d, a);
    }

    @PostMapping
    @Operation(summary = "Créer un chauffeur", description = "Ajoute un nouveau chauffeur à la flotte")
    @ApiResponse(responseCode = "200", description = "Chauffeur créé avec succès")
    @ApiResponse(responseCode = "409", description = "Numéro de permis déjà utilisé")
    @ApiResponse(responseCode = "403", description = "Limite de chauffeurs atteinte pour la formule actuelle")
    public DriverDto create(@Valid @RequestBody DriverRequest req) {
        Long companyId = TenantContext.companyId();
        if (driverRepository.findByLicenseNumberAndCompanyId(req.licenseNumber(), companyId).isPresent()) {
            throw new IllegalArgumentException("Ce numéro de permis est déjà utilisé");
        }
        Integer maxDrivers = TenantContext.require().getPlan().getMaxDrivers();
        if (maxDrivers != null && driverRepository.countByCompanyId(companyId) >= maxDrivers) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Limite de chauffeurs atteinte pour votre formule ("
                            + TenantContext.require().getPlan().name() + "). Contactez le support pour évoluer.");
        }
        Driver d = new Driver();
        d.setCompany(TenantContext.require());
        apply(d, req);
        return toDto(driverRepository.save(d), null);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un chauffeur", description = "Met à jour les informations d'un chauffeur existant")
    @ApiResponse(responseCode = "200", description = "Chauffeur mis à jour avec succès")
    @ApiResponse(responseCode = "404", description = "Chauffeur introuvable")
    @ApiResponse(responseCode = "409", description = "Numéro de permis déjà utilisé par un autre chauffeur")
    public DriverDto update(@PathVariable Long id, @Valid @RequestBody DriverRequest req) {
        Long companyId = TenantContext.companyId();
        Driver d = driverRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Chauffeur introuvable"));
        driverRepository.findByLicenseNumberAndCompanyId(req.licenseNumber(), companyId)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new IllegalArgumentException("Ce numéro de permis est déjà utilisé");
                });
        apply(d, req);
        return toDto(driverRepository.save(d), null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un chauffeur", description = "Supprime un chauffeur et toutes ses données associées")
    @ApiResponse(responseCode = "204", description = "Chauffeur supprimé avec succès")
    @ApiResponse(responseCode = "404", description = "Chauffeur introuvable")
    public void delete(@PathVariable Long id) {
        Long companyId = TenantContext.companyId();
        Driver d = driverRepository.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Chauffeur introuvable"));
        assignmentRepository.deleteByDriver(d);
        tripRepository.deleteByDriver(d);
        eventRepository.deleteByDriver(d);
        tachographRepository.deleteByDriver(d);
        costRecordRepository.deleteByDriver(d);
        driverRepository.delete(d);
    }

    private void apply(Driver d, DriverRequest req) {
        d.setFirstName(req.firstName());
        d.setLastName(req.lastName());
        d.setLicenseNumber(req.licenseNumber());
        d.setPhone(req.phone());
        d.setEmail(req.email());
        d.setHireDate(req.hireDate());
        d.setActive(req.active());
    }

    private DriverDto toDto(Driver d, DriverTruckAssignment a) {
        return new DriverDto(
                d.getId(), d.getFirstName(), d.getLastName(), d.getLicenseNumber(),
                d.getPhone(), d.getEmail(), d.getHireDate(), d.isActive(),
                a != null ? a.getTruck().getId() : null,
                a != null ? a.getTruck().getRegistration() : null);
    }
}
