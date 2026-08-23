package com.incokalk.service.erp;

import com.incokalk.dto.config.ErpConfigDTO;
import com.incokalk.dto.config.ErpHealthDTO;
import com.incokalk.dto.config.ErpSyncLogDTO;
import com.incokalk.dto.shared.SyncRequestDTO;
import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Company;
import com.incokalk.model.ErpConfig;
import com.incokalk.model.ErpSyncLog;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.ErpConfigRepository;
import com.incokalk.repository.ErpSyncLogRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ErpSyncService {

    private final ErpConfigRepository erpConfigRepo;
    private final ErpSyncLogRepository erpSyncLogRepo;
    private final CompanyRepository companyRepo;
    private final ShipmentOrderRepository shipmentOrderRepo;
    private final ErpProviderRegistry providerRegistry;

    public List<ErpConfig> listConfigs(UUID companyId) {
        return erpConfigRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    @Transactional
    public ErpConfig createConfig(ErpConfigDTO dto, UUID companyId) {
        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée"));

        ErpConfig config = ErpConfig.builder()
                .company(company)
                .erpType(dto.getErpType().toUpperCase())
                .name(dto.getName())
                .apiEndpoint(dto.getApiEndpoint())
                .apiKey(dto.getApiKey())
                .apiSecret(dto.getApiSecret())
                .databaseName(dto.getDatabaseName())
                .username(dto.getUsername())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .configJson(dto.getConfigJson())
                .syncStatus("IDLE")
                .build();

        return erpConfigRepo.save(config);
    }

    @Transactional
    public ErpConfig updateConfig(UUID id, ErpConfigDTO dto, UUID companyId) {
        ErpConfig config = erpConfigRepo.findById(id)
                .filter(c -> c.getCompany() != null && c.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Configuration ERP non trouvée"));

        if (dto.getName() != null) config.setName(dto.getName());
        if (dto.getApiEndpoint() != null) config.setApiEndpoint(dto.getApiEndpoint());
        if (dto.getApiKey() != null) config.setApiKey(dto.getApiKey());
        if (dto.getApiSecret() != null) config.setApiSecret(dto.getApiSecret());
        if (dto.getDatabaseName() != null) config.setDatabaseName(dto.getDatabaseName());
        if (dto.getUsername() != null) config.setUsername(dto.getUsername());
        if (dto.getIsActive() != null) config.setIsActive(dto.getIsActive());
        if (dto.getConfigJson() != null) config.setConfigJson(dto.getConfigJson());
        if (dto.getErpType() != null) config.setErpType(dto.getErpType().toUpperCase());

        return erpConfigRepo.save(config);
    }

    @Transactional
    public void deleteConfig(UUID id, UUID companyId) {
        ErpConfig config = erpConfigRepo.findById(id)
                .filter(c -> c.getCompany() != null && c.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Configuration ERP non trouvée"));
        erpConfigRepo.delete(config);
    }

    public boolean testConnection(UUID configId, UUID companyId) {
        ErpConfig config = erpConfigRepo.findById(configId)
                .filter(c -> c.getCompany() != null && c.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Configuration ERP non trouvée"));

        Optional<ErpProvider> providerOpt = providerRegistry.getProvider(config.getErpType());
        if (providerOpt.isEmpty()) {
            log.warn("[ERP] Aucun provider trouvé pour le type {}", config.getErpType());
            return false;
        }

        try {
            return providerOpt.get().testConnection(config);
        } catch (Exception e) {
            log.error("[ERP] Erreur test connexion {}: {}", config.getErpType(), e.getMessage());
            return false;
        }
    }

    @Transactional
    public ErpSyncLog sync(UUID configId, SyncRequestDTO dto, UUID companyId) {
        ErpConfig config = erpConfigRepo.findById(configId)
                .filter(c -> c.getCompany() != null && c.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Configuration ERP non trouvée"));

        Optional<ErpProvider> providerOpt = providerRegistry.getProvider(config.getErpType());
        if (providerOpt.isEmpty()) {
            throw new IllegalArgumentException("Aucun provider ERP trouvé pour le type: " + config.getErpType());
        }

        ErpSyncLog syncLog = ErpSyncLog.builder()
                .erpConfig(config)
                .company(config.getCompany())
                .syncType(dto.getSyncType().toUpperCase())
                .direction(dto.getDirection().toUpperCase())
                .status("STARTED")
                .build();
        syncLog = erpSyncLogRepo.save(syncLog);

        config.setSyncStatus("SYNCING");
        erpConfigRepo.save(config);

        try {
            ErpProvider provider = providerOpt.get();
            ErpSyncResult result = executeSync(provider, config, dto.getSyncType().toUpperCase(), dto.getDirection().toUpperCase());

            syncLog.setRecordsTotal(result.getRecordsTotal());
            syncLog.setRecordsSynced(result.getRecordsSynced());
            syncLog.setRecordsFailed(result.getRecordsFailed());
            syncLog.setStatus(result.isSuccess() ? "SUCCESS" : (result.getRecordsSynced() > 0 ? "PARTIAL" : "FAILED"));
            syncLog.setErrorMessage(result.getErrorMessage());
            syncLog.setCompletedAt(LocalDateTime.now());

            config.setSyncStatus(syncLog.getStatus());
            config.setLastSyncAt(LocalDateTime.now());
            if (!result.isSuccess()) {
                config.setLastError(result.getErrorMessage());
            }
        } catch (Exception e) {
            log.error("[ERP] Erreur sync {}: {}", config.getErpType(), e.getMessage());
            syncLog.setStatus("FAILED");
            syncLog.setErrorMessage(e.getMessage());
            syncLog.setCompletedAt(LocalDateTime.now());
            config.setSyncStatus("ERROR");
            config.setLastError(e.getMessage());
        }

        erpConfigRepo.save(config);
        return erpSyncLogRepo.save(syncLog);
    }

    private ErpSyncResult executeSync(ErpProvider provider, ErpConfig config, String syncType, String direction) {
        return switch (syncType) {
            case "PRODUCTS" -> {
                if ("EXPORT".equals(direction)) {
                    yield ErpSyncResult.builder().success(true).recordsTotal(0).recordsSynced(0).recordsFailed(0)
                            .warnings(List.of("Export de produits non supporté")).build();
                }
                yield provider.importProducts(config);
            }
            case "ORDERS" -> {
                if ("EXPORT".equals(direction)) {
                    List<ShipmentOrder> orders = shipmentOrderRepo.findByCompanyIdOrderByCreatedAtDesc(config.getCompany().getId());
                    yield provider.exportOrders(config, orders);
                }
                yield provider.importOrders(config);
            }
            case "INVOICES" -> {
                if ("EXPORT".equals(direction)) {
                    List<ShipmentOrder> shipments = shipmentOrderRepo.findByCompanyIdOrderByCreatedAtDesc(config.getCompany().getId());
                    yield provider.exportShipments(config, shipments);
                }
                yield provider.importOrders(config);
            }
            case "CONTACTS" -> {
                if ("EXPORT".equals(direction)) {
                    yield ErpSyncResult.builder().success(true).recordsTotal(0).recordsSynced(0).recordsFailed(0)
                            .warnings(List.of("Export de contacts non supporté")).build();
                }
                yield provider.importContacts(config);
            }
            case "SHIPMENTS" -> {
                if ("IMPORT".equals(direction)) {
                    yield provider.importOrders(config);
                }
                List<ShipmentOrder> shipments = shipmentOrderRepo.findByCompanyIdOrderByCreatedAtDesc(config.getCompany().getId());
                yield provider.exportShipments(config, shipments);
            }
            default -> ErpSyncResult.builder()
                    .success(false)
                    .errorMessage("Type de synchronisation inconnu: " + syncType)
                    .build();
        };
    }

    public List<ErpSyncLogDTO> getSyncLogs(UUID companyId) {
        List<ErpSyncLog> logs = erpSyncLogRepo.findTop5ByCompanyIdOrderByStartedAtDesc(companyId);
        return logs.stream().map(this::toSyncLogDTO).toList();
    }

    public List<ErpHealthDTO> getHealth(UUID companyId) {
        List<ErpConfig> configs = erpConfigRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
        return configs.stream().map(config -> ErpHealthDTO.builder()
                .id(config.getId())
                .erpType(config.getErpType())
                .name(config.getName())
                .syncStatus(config.getSyncStatus())
                .lastSyncAt(config.getLastSyncAt())
                .lastError(config.getLastError())
                .isActive(config.getIsActive())
                .build()).toList();
    }

    public List<Map<String, Object>> getProducts(UUID configId, UUID companyId) {
        ErpConfig config = getConfigForCompany(configId, companyId);
        Optional<ErpProvider> providerOpt = providerRegistry.getProvider(config.getErpType());
        if (providerOpt.isEmpty()) return List.of();
        try {
            return providerOpt.get().getProducts(config);
        } catch (Exception e) {
            log.error("[ERP] Erreur récupération produits {}: {}", config.getErpType(), e.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> getOrders(UUID configId, UUID companyId) {
        ErpConfig config = getConfigForCompany(configId, companyId);
        Optional<ErpProvider> providerOpt = providerRegistry.getProvider(config.getErpType());
        if (providerOpt.isEmpty()) return List.of();
        try {
            return providerOpt.get().getOrders(config);
        } catch (Exception e) {
            log.error("[ERP] Erreur récupération commandes {}: {}", config.getErpType(), e.getMessage());
            return List.of();
        }
    }

    public List<Map<String, Object>> getContacts(UUID configId, UUID companyId) {
        ErpConfig config = getConfigForCompany(configId, companyId);
        Optional<ErpProvider> providerOpt = providerRegistry.getProvider(config.getErpType());
        if (providerOpt.isEmpty()) return List.of();
        try {
            return providerOpt.get().getContacts(config);
        } catch (Exception e) {
            log.error("[ERP] Erreur récupération contacts {}: {}", config.getErpType(), e.getMessage());
            return List.of();
        }
    }

    private ErpConfig getConfigForCompany(UUID configId, UUID companyId) {
        return erpConfigRepo.findById(configId)
                .filter(c -> c.getCompany() != null && c.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Configuration ERP non trouvée"));
    }

    private ErpSyncLogDTO toSyncLogDTO(ErpSyncLog log) {
        return ErpSyncLogDTO.builder()
                .id(log.getId())
                .erpConfigId(log.getErpConfig() != null ? log.getErpConfig().getId() : null)
                .erpTypeName(log.getErpConfig() != null ? log.getErpConfig().getErpType() : null)
                .syncType(log.getSyncType())
                .direction(log.getDirection())
                .status(log.getStatus())
                .recordsTotal(log.getRecordsTotal())
                .recordsSynced(log.getRecordsSynced())
                .recordsFailed(log.getRecordsFailed())
                .errorMessage(log.getErrorMessage())
                .startedAt(log.getStartedAt())
                .completedAt(log.getCompletedAt())
                .build();
    }
}
