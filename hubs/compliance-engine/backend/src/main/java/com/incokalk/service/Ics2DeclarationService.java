package com.incokalk.service;

import com.incokalk.model.Ics2Declaration;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.Ics2DeclarationRepository;
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
public class Ics2DeclarationService {

    private final Ics2DeclarationRepository ics2Repo;
    private final CompanyRepository companyRepo;
    private final DeclarationValidationService validationService;

    public List<Ics2Declaration> getAll() {
        UUID companyId = TenantContext.get();
        return ics2Repo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    public Ics2Declaration getById(UUID id) {
        UUID companyId = TenantContext.get();
        return ics2Repo.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new IllegalArgumentException("Déclaration ICS2 introuvable"));
    }

    @Transactional
    public Ics2Declaration create(Ics2Declaration declaration) {
        UUID companyId = TenantContext.get();

        companyRepo.findById(companyId)
            .orElseThrow(() -> new IllegalArgumentException("Entreprise introuvable"));

        declaration.setCompany(companyRepo.getReferenceById(companyId));
        declaration.setStatus(Ics2Declaration.Ics2Status.DRAFT);

        String number = generateDeclarationNumber(companyId);
        declaration.setDeclarationNumber(number);

        ics2Repo.save(declaration);
        log.info("Déclaration ICS2 {} créée pour company {}", number, companyId);
        return declaration;
    }

    @Transactional
    public Ics2Declaration update(UUID id, Ics2Declaration updated) {
        UUID companyId = TenantContext.get();
        Ics2Declaration declaration = ics2Repo.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new IllegalArgumentException("Déclaration ICS2 introuvable"));

        if (declaration.getStatus() != Ics2Declaration.Ics2Status.DRAFT) {
            throw new IllegalArgumentException("Seule une déclaration en brouillon peut être modifiée");
        }

        if (updated.getSenderEori() != null) declaration.setSenderEori(updated.getSenderEori());
        if (updated.getReceiverEori() != null) declaration.setReceiverEori(updated.getReceiverEori());
        if (updated.getVesselName() != null) declaration.setVesselName(updated.getVesselName());
        if (updated.getVoyageNumber() != null) declaration.setVoyageNumber(updated.getVoyageNumber());
        if (updated.getContainerNumber() != null) declaration.setContainerNumber(updated.getContainerNumber());
        if (updated.getHsCode6() != null) declaration.setHsCode6(updated.getHsCode6());
        if (updated.getGoodsDescription() != null) declaration.setGoodsDescription(updated.getGoodsDescription());
        if (updated.getGrossWeight() != null) declaration.setGrossWeight(updated.getGrossWeight());
        if (updated.getPackagesCount() != null) declaration.setPackagesCount(updated.getPackagesCount());

        return ics2Repo.save(declaration);
    }

    @Transactional
    public Ics2Declaration updateStatus(UUID id, Ics2Declaration.Ics2Status newStatus) {
        UUID companyId = TenantContext.get();
        Ics2Declaration declaration = ics2Repo.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new IllegalArgumentException("Déclaration ICS2 introuvable"));

        Ics2Declaration.Ics2Status currentStatus = declaration.getStatus();

        boolean validTransition = switch (currentStatus) {
            case DRAFT -> newStatus == Ics2Declaration.Ics2Status.SENT;
            case SENT -> newStatus == Ics2Declaration.Ics2Status.PENDING
                      || newStatus == Ics2Declaration.Ics2Status.ACCEPTED
                      || newStatus == Ics2Declaration.Ics2Status.REJECTED;
            case PENDING -> newStatus == Ics2Declaration.Ics2Status.ACCEPTED
                         || newStatus == Ics2Declaration.Ics2Status.REJECTED;
            case ACCEPTED, REJECTED -> false;
        };

        if (!validTransition) {
            throw new IllegalArgumentException(
                "Transition invalide : " + currentStatus + " → " + newStatus);
        }

        if (newStatus == Ics2Declaration.Ics2Status.SENT) {
            List<String> errors = validationService.validateIcs2(declaration).stream()
                .filter(a -> "ERROR".equals(a.level()))
                .map(DeclarationValidationService.Alert::message)
                .toList();
            if (!errors.isEmpty()) {
                throw new IllegalStateException(
                    "Déclaration invalide, corrigez avant envoi : " + String.join("; ", errors));
            }
        }

        declaration.setStatus(newStatus);

        if (newStatus == Ics2Declaration.Ics2Status.SENT) {
            declaration.setSubmittedAt(LocalDateTime.now());
        }

        if (newStatus == Ics2Declaration.Ics2Status.ACCEPTED || newStatus == Ics2Declaration.Ics2Status.REJECTED) {
            declaration.setRespondedAt(LocalDateTime.now());
        }

        ics2Repo.save(declaration);
        log.info("Déclaration ICS2 {} mise à jour : {} → {}", declaration.getDeclarationNumber(), currentStatus, newStatus);
        return declaration;
    }

    @Transactional
    public void delete(UUID id) {
        UUID companyId = TenantContext.get();
        Ics2Declaration declaration = ics2Repo.findByCompanyIdAndId(companyId, id)
            .orElseThrow(() -> new IllegalArgumentException("Déclaration ICS2 introuvable"));

        if (declaration.getStatus() != Ics2Declaration.Ics2Status.DRAFT) {
            throw new IllegalArgumentException("Seules les déclarations en DRAFT peuvent être supprimées");
        }

        ics2Repo.delete(declaration);
        log.info("Déclaration ICS2 {} supprimée pour company {}", declaration.getDeclarationNumber(), companyId);
    }

    public Map<String, Object> getStats() {
        UUID companyId = TenantContext.get();
        Map<String, Object> stats = new LinkedHashMap<>();

        for (Ics2Declaration.Ics2Status status : Ics2Declaration.Ics2Status.values()) {
            long count = ics2Repo.countByCompanyIdAndStatus(companyId, status);
            stats.put(status.name(), count);
        }

        return stats;
    }

    private String generateDeclarationNumber(UUID companyId) {
        long total = ics2Repo.findByCompanyIdOrderByCreatedAtDesc(companyId).size();
        long nextSeq = total + 1;
        int year = LocalDateTime.now().getYear();

        return "ICS2-" + year + "-" + String.format("%03d", nextSeq);
    }
}
