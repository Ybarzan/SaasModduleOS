package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Warehouse;
import com.incokalk.repository.WarehouseRepository;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseService {

    private final WarehouseRepository warehouseRepo;

    public List<Warehouse> getAll() {
        return warehouseRepo.findByCompanyId(TenantContext.get());
    }

    public Warehouse getById(UUID id) {
        return warehouseRepo.findByCompanyIdAndId(TenantContext.get(), id)
                .orElseThrow(() -> new ResourceNotFoundException("Entrepôt non trouvé"));
    }

    @Transactional
    public Warehouse create(Warehouse warehouse) {
        warehouse.setId(null);
        warehouse.setCompanyId(TenantContext.get());
        if (warehouse.getName() == null || warehouse.getName().isBlank()) {
            throw new IllegalArgumentException("Le nom de l'entrepôt est requis");
        }
        return warehouseRepo.save(warehouse);
    }

    @Transactional
    public Warehouse update(UUID id, Warehouse updated) {
        Warehouse existing = getById(id);
        existing.setName(updated.getName());
        existing.setCode(updated.getCode());
        existing.setBranchId(updated.getBranchId());
        existing.setAddress(updated.getAddress());
        existing.setCity(updated.getCity());
        existing.setCountry(updated.getCountry());
        existing.setActive(updated.isActive());
        return warehouseRepo.save(existing);
    }

    @Transactional
    public void delete(UUID id) {
        Warehouse existing = getById(id);
        existing.setActive(false);
        warehouseRepo.save(existing);
    }
}
