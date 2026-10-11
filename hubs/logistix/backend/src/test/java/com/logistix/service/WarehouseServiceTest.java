package com.logistix.service;

import com.logistix.exception.ResourceNotFoundException;
import com.logistix.model.Warehouse;
import com.logistix.repository.WarehouseRepository;
import com.logistix.security.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/**
 * compliance-engine n'avait pas de test dédié pour WarehouseService : ce
 * fichier est un test de substitution (CRUD de base), pas un portage.
 */
@DisplayName("WarehouseService — Tests unitaires")
class WarehouseServiceTest {

    @Mock WarehouseRepository warehouseRepo;

    @InjectMocks WarehouseService service;

    UUID companyId;
    UUID warehouseId;
    Warehouse warehouse;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        warehouse = Warehouse.builder().id(warehouseId).companyId(companyId).name("Entrepôt Nord").build();
    }

    @Test
    @DisplayName("getAll → liste des entrepôts de la company")
    void getAll() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(warehouseRepo.findByCompanyId(companyId)).thenReturn(List.of(warehouse));
            assertThat(service.getAll()).containsExactly(warehouse);
        }
    }

    @Test
    @DisplayName("create → sauvegarde avec companyId assigné")
    void create() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(warehouseRepo.save(any(Warehouse.class))).thenAnswer(a -> a.getArgument(0));

            Warehouse created = service.create(Warehouse.builder().name("Nouvel entrepôt").build());

            assertThat(created.getCompanyId()).isEqualTo(companyId);
            assertThat(created.getId()).isNull(); // assigné par la base, pas par le service
        }
    }

    @Test
    @DisplayName("create sans nom → IllegalArgumentException")
    void createBlankName() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            assertThatThrownBy(() -> service.create(Warehouse.builder().build()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("getById inconnu → ResourceNotFoundException")
    void getByIdNotFound() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(warehouseRepo.findByCompanyIdAndId(companyId, warehouseId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.getById(warehouseId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("update → met à jour les champs modifiables")
    void update() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(warehouseRepo.findByCompanyIdAndId(companyId, warehouseId)).thenReturn(Optional.of(warehouse));
            when(warehouseRepo.save(any(Warehouse.class))).thenAnswer(a -> a.getArgument(0));

            Warehouse updated = service.update(warehouseId,
                    Warehouse.builder().name("Entrepôt Sud").city("Lyon").isActive(true).build());

            assertThat(updated.getName()).isEqualTo("Entrepôt Sud");
            assertThat(updated.getCity()).isEqualTo("Lyon");
        }
    }

    @Test
    @DisplayName("delete → désactive au lieu de supprimer")
    void delete() {
        try (MockedStatic<TenantContext> ctx = mockStatic(TenantContext.class)) {
            ctx.when(TenantContext::get).thenReturn(companyId);
            when(warehouseRepo.findByCompanyIdAndId(companyId, warehouseId)).thenReturn(Optional.of(warehouse));
            when(warehouseRepo.save(any(Warehouse.class))).thenAnswer(a -> a.getArgument(0));

            service.delete(warehouseId);

            assertThat(warehouse.isActive()).isFalse();
            verify(warehouseRepo, times(1)).save(warehouse);
            verify(warehouseRepo, never()).delete(any());
        }
    }
}
