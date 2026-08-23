package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.CustomsDeclaration;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.CustomsDeclarationRepository;
import com.incokalk.repository.EoriNumberRepository;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomsDeclarationService {

    private final CustomsDeclarationRepository declarationRepo;
    private final CompanyRepository companyRepo;
    private final EoriNumberRepository eoriRepo;
    private final DeclarationValidationService validationService;

    public List<CustomsDeclaration> getAll() {
        UUID companyId = TenantContext.get();
        return declarationRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    public Page<CustomsDeclaration> getAll(Pageable pageable) {
        UUID companyId = TenantContext.get();
        return declarationRepo.findByCompanyIdOrderByCreatedAtDesc(companyId, pageable);
    }

    public CustomsDeclaration getById(UUID id) {
        UUID companyId = TenantContext.get();
        return declarationRepo.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new IllegalArgumentException("Déclaration introuvable"));
    }

    @Transactional
    public CustomsDeclaration create(CustomsDeclaration declaration) {
        UUID companyId = TenantContext.get();
        Company company = companyRepo.findById(companyId)
            .orElseThrow(() -> new IllegalArgumentException("Entreprise introuvable"));

        declaration.setCompany(company);
        declaration.setStatus(CustomsDeclaration.DeclarationStatus.DRAFT);
        declaration.setDeclarationNumber(generateDeclarationNumber(companyId));

        return declarationRepo.save(declaration);
    }

    @Transactional
    public CustomsDeclaration update(UUID id, CustomsDeclaration updated) {
        UUID companyId = TenantContext.get();
        CustomsDeclaration declaration = declarationRepo.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new IllegalArgumentException("Déclaration introuvable"));

        if (declaration.getStatus() != CustomsDeclaration.DeclarationStatus.DRAFT) {
            throw new IllegalArgumentException("Seule une déclaration en brouillon peut être modifiée");
        }

        if (updated.getDeclarationType() != null) declaration.setDeclarationType(updated.getDeclarationType());
        if (updated.getCustomsOffice() != null) declaration.setCustomsOffice(updated.getCustomsOffice());
        if (updated.getCustomsRegime() != null) declaration.setCustomsRegime(updated.getCustomsRegime());
        if (updated.getCustomsCode() != null) declaration.setCustomsCode(updated.getCustomsCode());
        if (updated.getDeclaredValue() != null) declaration.setDeclaredValue(updated.getDeclaredValue());
        if (updated.getCurrency() != null) declaration.setCurrency(updated.getCurrency());
        if (updated.getOriginCountry() != null) declaration.setOriginCountry(updated.getOriginCountry());
        if (updated.getDestinationCountry() != null) declaration.setDestinationCountry(updated.getDestinationCountry());
        if (updated.getHsCode() != null) declaration.setHsCode(updated.getHsCode());
        if (updated.getGoodsDescription() != null) declaration.setGoodsDescription(updated.getGoodsDescription());
        if (updated.getNetWeight() != null) declaration.setNetWeight(updated.getNetWeight());
        if (updated.getGrossWeight() != null) declaration.setGrossWeight(updated.getGrossWeight());
        if (updated.getPackages() != null) declaration.setPackages(updated.getPackages());
        if (updated.getNotes() != null) declaration.setNotes(updated.getNotes());

        return declarationRepo.save(declaration);
    }

    @Transactional
    public CustomsDeclaration updateStatus(UUID id, CustomsDeclaration.DeclarationStatus newStatus) {
        UUID companyId = TenantContext.get();
        CustomsDeclaration declaration = declarationRepo.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new IllegalArgumentException("Déclaration introuvable"));

        validateStatusTransition(declaration.getStatus(), newStatus);

        if (newStatus == CustomsDeclaration.DeclarationStatus.SUBMITTED) {
            List<String> errors = validationService.validateDau(declaration).stream()
                .filter(a -> "ERROR".equals(a.level()))
                .map(DeclarationValidationService.Alert::message)
                .toList();
            if (!errors.isEmpty()) {
                throw new IllegalStateException(
                    "Déclaration invalide, corrigez avant soumission : " + String.join("; ", errors));
            }
        }

        declaration.setStatus(newStatus);

        LocalDateTime now = LocalDateTime.now();
        switch (newStatus) {
            case SUBMITTED -> declaration.setSubmittedAt(now);
            case CLEARED -> declaration.setClearedAt(now);
            case REJECTED -> declaration.setRejectedAt(now);
            default -> {}
        }

        return declarationRepo.save(declaration);
    }

    @Transactional
    public void delete(UUID id) {
        UUID companyId = TenantContext.get();
        CustomsDeclaration declaration = declarationRepo.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new IllegalArgumentException("Déclaration introuvable"));

        if (declaration.getStatus() != CustomsDeclaration.DeclarationStatus.DRAFT) {
            throw new IllegalArgumentException("Seules les déclarations en brouillon peuvent être supprimées");
        }

        declarationRepo.delete(declaration);
        log.info("Déclaration {} supprimée pour company {}", declaration.getDeclarationNumber(), companyId);
    }

    public Map<String, Object> getStats() {
        UUID companyId = TenantContext.get();
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", declarationRepo.countByCompanyId(companyId));
        stats.put("draft", declarationRepo.countByCompanyIdAndStatus(companyId, CustomsDeclaration.DeclarationStatus.DRAFT));
        stats.put("submitted", declarationRepo.countByCompanyIdAndStatus(companyId, CustomsDeclaration.DeclarationStatus.SUBMITTED));
        stats.put("underReview", declarationRepo.countByCompanyIdAndStatus(companyId, CustomsDeclaration.DeclarationStatus.UNDER_REVIEW));
        stats.put("cleared", declarationRepo.countByCompanyIdAndStatus(companyId, CustomsDeclaration.DeclarationStatus.CLEARED));
        stats.put("released", declarationRepo.countByCompanyIdAndStatus(companyId, CustomsDeclaration.DeclarationStatus.RELEASED));
        stats.put("rejected", declarationRepo.countByCompanyIdAndStatus(companyId, CustomsDeclaration.DeclarationStatus.REJECTED));
        return stats;
    }

    private void validateStatusTransition(CustomsDeclaration.DeclarationStatus current, CustomsDeclaration.DeclarationStatus next) {
        boolean valid = switch (current) {
            case DRAFT -> next == CustomsDeclaration.DeclarationStatus.SUBMITTED;
            case SUBMITTED -> next == CustomsDeclaration.DeclarationStatus.UNDER_REVIEW;
            case UNDER_REVIEW -> next == CustomsDeclaration.DeclarationStatus.CLEARED || next == CustomsDeclaration.DeclarationStatus.REJECTED;
            case CLEARED -> next == CustomsDeclaration.DeclarationStatus.RELEASED;
            default -> false;
        };

        if (!valid) {
            throw new IllegalArgumentException(
                "Transition invalide: " + current + " → " + next);
        }
    }

    private String generateDeclarationNumber(UUID companyId) {
        long count = declarationRepo.countByCompanyId(companyId) + 1;
        String year = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy"));
        return "DAU-" + year + "-" + String.format("%04d", count);
    }
}
