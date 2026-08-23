package com.fleethub.controller;

import com.fleethub.config.ResourceNotFoundException;
import com.fleethub.dto.FuelRecordDto;
import com.fleethub.dto.FuelRecordRequest;
import com.fleethub.model.FuelRecord;
import com.fleethub.model.Truck;
import com.fleethub.repository.FuelRecordRepository;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/fuel-records")
@RequiredArgsConstructor
@Tag(name = "Carburant", description = "Relevés de consommation de carburant de la flotte")
public class FuelRecordController {

    private final FuelRecordRepository fuelRecordRepository;
    private final TruckRepository truckRepository;

    @GetMapping
    @Operation(summary = "Lister les relevés carburant", description = "Retourne les relevés avec filtres optionnels (camion, période)")
    public List<FuelRecordDto> all(@RequestParam(required = false) Long truckId,
                                   @RequestParam(required = false) String from,
                                   @RequestParam(required = false) String to) {
        LocalDate fromDate = from != null ? parseDate(from) : null;
        LocalDate toDate = to != null ? parseDate(to) : null;
        return fuelRecordRepository.findAllFetch(TenantContext.companyId()).stream()
                .filter(r -> truckId == null || r.getTruck().getId().equals(truckId))
                .filter(r -> fromDate == null || !r.getDate().isBefore(fromDate))
                .filter(r -> toDate == null || !r.getDate().isAfter(toDate))
                .map(this::toDto)
                .toList();
    }

    @PostMapping
    @Operation(summary = "Créer un relevé carburant", description = "Enregistre un nouveau relevé de pleins")
    @Transactional
    public FuelRecordDto create(@Valid @RequestBody FuelRecordRequest req) {
        Truck truck = truckRepository.findByIdAndCompanyId(req.truckId(), TenantContext.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Camion introuvable"));
        FuelRecord r = new FuelRecord();
        r.setCompany(TenantContext.require());
        apply(r, req, truck);
        return toDto(fuelRecordRepository.save(r));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un relevé carburant", description = "Met à jour les informations d'un relevé de plein")
    @Transactional
    public FuelRecordDto update(@PathVariable Long id, @Valid @RequestBody FuelRecordRequest req) {
        FuelRecord r = fuelRecordRepository.findByIdAndCompanyId(id, TenantContext.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Relevé de carburant introuvable"));
        Truck truck = truckRepository.findByIdAndCompanyId(req.truckId(), TenantContext.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Camion introuvable"));
        apply(r, req, truck);
        return toDto(fuelRecordRepository.save(r));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un relevé carburant", description = "Supprime un relevé de plein")
    @Transactional
    public void delete(@PathVariable Long id) {
        FuelRecord r = fuelRecordRepository.findByIdAndCompanyId(id, TenantContext.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Relevé de carburant introuvable"));
        fuelRecordRepository.delete(r);
    }

    private LocalDate parseDate(String value) {
        return value.length() > 10 ? LocalDateTime.parse(value).toLocalDate() : LocalDate.parse(value);
    }

    private void apply(FuelRecord r, FuelRecordRequest req, Truck truck) {
        r.setTruck(truck);
        r.setDate(req.date());
        r.setLiters(req.liters());
        r.setAmount(req.amount());
        r.setOdometerKm(req.odometerKm());
    }

    private FuelRecordDto toDto(FuelRecord r) {
        return new FuelRecordDto(
                r.getId(), r.getTruck().getId(), r.getTruck().getRegistration(),
                r.getDate(), r.getLiters(), r.getAmount(), r.getOdometerKm());
    }
}
