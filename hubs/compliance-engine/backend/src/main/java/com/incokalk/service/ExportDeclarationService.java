package com.incokalk.service;

import com.incokalk.model.ExportDeclaration;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.ExportDeclarationRepository;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExportDeclarationService {

    private final ExportDeclarationRepository exportRepo;
    private final CompanyRepository companyRepo;
    private final DeclarationValidationService validationService;

    public List<ExportDeclaration> getAll() {
        UUID companyId = TenantContext.get();
        return exportRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    public ExportDeclaration getById(UUID id) {
        UUID companyId = TenantContext.get();
        return exportRepo.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new IllegalArgumentException("Déclaration export introuvable"));
    }

    @Transactional
    public ExportDeclaration create(ExportDeclaration declaration) {
        UUID companyId = TenantContext.get();

        companyRepo.findById(companyId)
            .orElseThrow(() -> new IllegalArgumentException("Entreprise introuvable"));

        declaration.setCompany(companyRepo.getReferenceById(companyId));
        declaration.setStatus(ExportDeclaration.ExportStatus.DRAFT);

        String number = generateDeclarationNumber(companyId);
        declaration.setDeclarationNumber(number);

        exportRepo.save(declaration);
        log.info("Déclaration export {} créée pour company {}", number, companyId);
        return declaration;
    }

    @Transactional
    public ExportDeclaration update(UUID id, ExportDeclaration updated) {
        UUID companyId = TenantContext.get();
        ExportDeclaration declaration = exportRepo.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new IllegalArgumentException("Déclaration export introuvable"));

        if (declaration.getStatus() != ExportDeclaration.ExportStatus.DRAFT) {
            throw new IllegalArgumentException("Seule une déclaration en brouillon peut être modifiée");
        }

        if (updated.getDeclarationType() != null) declaration.setDeclarationType(updated.getDeclarationType());
        if (updated.getExporterEori() != null) declaration.setExporterEori(updated.getExporterEori());
        if (updated.getDestinationCountry() != null) declaration.setDestinationCountry(updated.getDestinationCountry());
        if (updated.getGoodsDescription() != null) declaration.setGoodsDescription(updated.getGoodsDescription());
        if (updated.getHsCode() != null) declaration.setHsCode(updated.getHsCode());
        if (updated.getDeclaredValue() != null) declaration.setDeclaredValue(updated.getDeclaredValue());
        if (updated.getCurrency() != null) declaration.setCurrency(updated.getCurrency());
        if (updated.getNetWeight() != null) declaration.setNetWeight(updated.getNetWeight());
        if (updated.getGrossWeight() != null) declaration.setGrossWeight(updated.getGrossWeight());
        if (updated.getPackagesCount() != null) declaration.setPackagesCount(updated.getPackagesCount());

        return exportRepo.save(declaration);
    }

    @Transactional
    public ExportDeclaration updateStatus(UUID id, ExportDeclaration.ExportStatus newStatus) {
        UUID companyId = TenantContext.get();
        ExportDeclaration declaration = exportRepo.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new IllegalArgumentException("Déclaration export introuvable"));

        ExportDeclaration.ExportStatus currentStatus = declaration.getStatus();

        boolean validTransition = switch (currentStatus) {
            case DRAFT -> newStatus == ExportDeclaration.ExportStatus.SUBMITTED;
            case SUBMITTED -> newStatus == ExportDeclaration.ExportStatus.VALIDATED
                          || newStatus == ExportDeclaration.ExportStatus.REJECTED;
            case VALIDATED, REJECTED -> false;
        };

        if (!validTransition) {
            throw new IllegalArgumentException(
                "Transition invalide : " + currentStatus + " → " + newStatus);
        }

        if (newStatus == ExportDeclaration.ExportStatus.SUBMITTED) {
            List<String> errors = validationService.validateExport(declaration).stream()
                .filter(a -> "ERROR".equals(a.level()))
                .map(DeclarationValidationService.Alert::message)
                .toList();
            if (!errors.isEmpty()) {
                throw new IllegalStateException(
                    "Déclaration invalide, corrigez avant soumission : " + String.join("; ", errors));
            }
        }

        declaration.setStatus(newStatus);

        if (newStatus == ExportDeclaration.ExportStatus.SUBMITTED) {
            declaration.setSubmittedAt(LocalDateTime.now());
        }

        if (newStatus == ExportDeclaration.ExportStatus.VALIDATED) {
            declaration.setValidatedAt(LocalDateTime.now());
        }

        if (newStatus == ExportDeclaration.ExportStatus.REJECTED) {
            declaration.setRejectedAt(LocalDateTime.now());
        }

        exportRepo.save(declaration);
        log.info("Déclaration export {} mise à jour : {} → {}", declaration.getDeclarationNumber(), currentStatus, newStatus);
        return declaration;
    }

    @Transactional
    public void delete(UUID id) {
        UUID companyId = TenantContext.get();
        ExportDeclaration declaration = exportRepo.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new IllegalArgumentException("Déclaration export introuvable"));

        if (declaration.getStatus() != ExportDeclaration.ExportStatus.DRAFT) {
            throw new IllegalArgumentException("Seules les déclarations en DRAFT peuvent être supprimées");
        }

        exportRepo.delete(declaration);
        log.info("Déclaration export {} supprimée pour company {}", declaration.getDeclarationNumber(), companyId);
    }

    public Map<String, Object> getStats() {
        UUID companyId = TenantContext.get();
        Map<String, Object> stats = new LinkedHashMap<>();

        for (ExportDeclaration.ExportStatus status : ExportDeclaration.ExportStatus.values()) {
            long count = exportRepo.countByCompanyIdAndStatus(companyId, status);
            stats.put(status.name(), count);
        }

        return stats;
    }

    private String generateDeclarationNumber(UUID companyId) {
        long total = exportRepo.findByCompanyIdOrderByCreatedAtDesc(companyId).size();
        long nextSeq = total + 1;
        int year = LocalDateTime.now().getYear();

        return "EXP-" + year + "-" + String.format("%03d", nextSeq);
    }
}
