package com.logistix.controller;

import com.logistix.model.Warehouse;
import com.logistix.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/v1/warehouses")
@RequiredArgsConstructor
@Tag(name = "Warehouses", description = "Entrepôts de réception")
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @Operation(summary = "Lister les entrepôts")
    public ResponseEntity<List<Warehouse>> listAll() {
        return ResponseEntity.ok(warehouseService.getAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','USER')")
    @Operation(summary = "Obtenir un entrepôt")
    public ResponseEntity<Warehouse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(warehouseService.getById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Créer un entrepôt")
    public ResponseEntity<Warehouse> create(@Valid @RequestBody CreateWarehouse body) {
        Warehouse warehouse = Warehouse.builder()
                .name(body.name())
                .code(body.code())
                .branchId(body.branchId())
                .address(body.address())
                .city(body.city())
                .country(body.country())
                .isActive(body.active() != null ? body.active() : true)
                .build();
        return ResponseEntity.ok(warehouseService.create(warehouse));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    @Operation(summary = "Mettre à jour un entrepôt")
    public ResponseEntity<Warehouse> update(@PathVariable UUID id, @Valid @RequestBody CreateWarehouse body) {
        Warehouse warehouse = Warehouse.builder()
                .name(body.name())
                .code(body.code())
                .branchId(body.branchId())
                .address(body.address())
                .city(body.city())
                .country(body.country())
                .isActive(body.active() != null ? body.active() : true)
                .build();
        return ResponseEntity.ok(warehouseService.update(id, warehouse));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Désactiver un entrepôt")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        warehouseService.delete(id);
        return ResponseEntity.noContent().build();
    }

    public record CreateWarehouse(
            @NotBlank String name,
            String code,
            UUID branchId,
            String address,
            String city,
            String country,
            Boolean active
    ) {}
}
