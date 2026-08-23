package com.incokalk.service;

import com.incokalk.model.DebDeclaration;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.DebDeclarationRepository;
import com.incokalk.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DebDeclarationService {

    private final DebDeclarationRepository debRepo;
    private final CompanyRepository companyRepo;
    private final DeclarationValidationService validationService;

    public List<DebDeclaration> getAll() {
        UUID companyId = TenantContext.get();
        return debRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    public DebDeclaration getById(UUID id) {
        UUID companyId = TenantContext.get();
        return debRepo.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new IllegalArgumentException("Déclaration DEB introuvable"));
    }

    @Transactional
    public DebDeclaration create(DebDeclaration declaration) {
        UUID companyId = TenantContext.get();

        companyRepo.findById(companyId)
            .orElseThrow(() -> new IllegalArgumentException("Entreprise introuvable"));

        declaration.setCompany(companyRepo.getReferenceById(companyId));
        declaration.setStatus(DebDeclaration.DebStatus.DRAFT);

        String number = generateDeclarationNumber(companyId);
        declaration.setDeclarationNumber(number);

        debRepo.save(declaration);
        log.info("Déclaration DEB {} créée pour company {}", number, companyId);
        return declaration;
    }

    @Transactional
    public DebDeclaration update(UUID id, DebDeclaration updated) {
        UUID companyId = TenantContext.get();
        DebDeclaration declaration = debRepo.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new IllegalArgumentException("Déclaration DEB introuvable"));

        if (declaration.getStatus() != DebDeclaration.DebStatus.DRAFT) {
            throw new IllegalArgumentException("Seule une déclaration en brouillon peut être modifiée");
        }

        if (updated.getDeclarationType() != null) declaration.setDeclarationType(updated.getDeclarationType());
        if (updated.getPeriod() != null) declaration.setPeriod(updated.getPeriod());
        if (updated.getPartnerCountry() != null) declaration.setPartnerCountry(updated.getPartnerCountry());
        if (updated.getNatureOfTransaction() != null) declaration.setNatureOfTransaction(updated.getNatureOfTransaction());
        if (updated.getModeOfTransport() != null) declaration.setModeOfTransport(updated.getModeOfTransport());
        if (updated.getNetMass() != null) declaration.setNetMass(updated.getNetMass());
        if (updated.getStatisticalValue() != null) declaration.setStatisticalValue(updated.getStatisticalValue());
        if (updated.getHsCode8() != null) declaration.setHsCode8(updated.getHsCode8());
        if (updated.getGoodsDescription() != null) declaration.setGoodsDescription(updated.getGoodsDescription());

        return debRepo.save(declaration);
    }

    public Map<String, Object> getStats() {
        UUID companyId = TenantContext.get();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", debRepo.countByCompanyId(companyId));
        stats.put("draft", debRepo.countByCompanyIdAndStatus(companyId, DebDeclaration.DebStatus.DRAFT));
        stats.put("validated", debRepo.countByCompanyIdAndStatus(companyId, DebDeclaration.DebStatus.VALIDATED));
        stats.put("submitted", debRepo.countByCompanyIdAndStatus(companyId, DebDeclaration.DebStatus.SUBMITTED));
        return stats;
    }

    @Transactional
    public DebDeclaration updateStatus(UUID id, DebDeclaration.DebStatus newStatus) {
        UUID companyId = TenantContext.get();
        DebDeclaration declaration = debRepo.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new IllegalArgumentException("Déclaration DEB introuvable"));

        DebDeclaration.DebStatus currentStatus = declaration.getStatus();

        boolean validTransition = switch (currentStatus) {
            case DRAFT -> newStatus == DebDeclaration.DebStatus.VALIDATED;
            case VALIDATED -> newStatus == DebDeclaration.DebStatus.SUBMITTED;
            case SUBMITTED -> false;
        };

        if (!validTransition) {
            throw new IllegalArgumentException(
                "Transition invalide : " + currentStatus + " → " + newStatus);
        }

        if (newStatus == DebDeclaration.DebStatus.VALIDATED) {
            List<String> errors = validationService.validateDeb(declaration).stream()
                .filter(a -> "ERROR".equals(a.level()))
                .map(DeclarationValidationService.Alert::message)
                .toList();
            if (!errors.isEmpty()) {
                throw new IllegalStateException(
                    "Déclaration invalide, corrigez avant validation : " + String.join("; ", errors));
            }
        }

        declaration.setStatus(newStatus);

        if (newStatus == DebDeclaration.DebStatus.SUBMITTED) {
            declaration.setSubmittedAt(LocalDateTime.now());
        }

        debRepo.save(declaration);
        log.info("Déclaration DEB {} mise à jour : {} → {}", declaration.getDeclarationNumber(), currentStatus, newStatus);
        return declaration;
    }

    @Transactional
    public void delete(UUID id) {
        UUID companyId = TenantContext.get();
        DebDeclaration declaration = debRepo.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new IllegalArgumentException("Déclaration DEB introuvable"));

        if (declaration.getStatus() != DebDeclaration.DebStatus.DRAFT) {
            throw new IllegalArgumentException("Seules les déclarations en DRAFT peuvent être supprimées");
        }

        debRepo.delete(declaration);
        log.info("Déclaration DEB {} supprimée pour company {}", declaration.getDeclarationNumber(), companyId);
    }

    public List<DebDeclaration> getByPeriod(String period) {
        UUID companyId = TenantContext.get();
        return debRepo.findByCompanyIdAndPeriod(companyId, period);
    }

    private String generateDeclarationNumber(UUID companyId) {
        LocalDateTime now = LocalDateTime.now();
        int year = now.getYear();
        int month = now.getMonthValue();
        String period = year + "-" + String.format("%02d", month);

        long existingCount = debRepo.countByCompanyIdAndStatus(companyId, DebDeclaration.DebStatus.DRAFT)
            + debRepo.countByCompanyIdAndStatus(companyId, DebDeclaration.DebStatus.VALIDATED)
            + debRepo.countByCompanyIdAndStatus(companyId, DebDeclaration.DebStatus.SUBMITTED);

        long nextSeq = existingCount + 1;

        return "DEB-" + period + "-" + String.format("%03d", nextSeq);
    }
}
