package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.EoriNumber;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.EoriNumberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class EoriService {

    private final EoriNumberRepository eoriRepo;
    private final CompanyRepository companyRepo;
    private final EoriOnlineService eoriOnlineService;

    private static final Pattern EU_EORI_PATTERN = Pattern.compile("^[A-Z]{2}\\d{8,15}$");

    @Transactional
    public EoriNumber create(UUID companyId, String eori, String holderName,
                             String holderAddress, String holderCountry, boolean isDefault) {
        String eoriUpper = eori.toUpperCase().replaceAll("\\s+", "");

        if (!EU_EORI_PATTERN.matcher(eoriUpper).matches()) {
            throw new IllegalArgumentException("Format EORI invalide. Attendu : 2 lettres + 8-15 chiffres (ex: FR123456789012345)");
        }

        if (eoriRepo.existsByCompanyIdAndEori(companyId, eoriUpper)) {
            throw new IllegalArgumentException("Cet EORI existe déjà pour cette entreprise");
        }

        EoriOnlineService.EoriCheck online = eoriOnlineService.checkEori(eoriUpper);
        boolean isValid = true;
        if (online.message() == null) {
            if (!online.valid()) {
                throw new IllegalArgumentException("EORI non valide (rejeté par le registre européen EORI)");
            }
            isValid = true;
        } else if (online.message().contains("disabled")) {
            log.debug("[EORI] Validation en ligne désactivée, format seul pour {}", eoriUpper);
        } else {
            log.warn("[EORI] Vérification en ligne indisponible pour {} ({}), format seul", eoriUpper, online.message());
        }

        Company company = companyRepo.findById(companyId)
            .orElseThrow(() -> new IllegalArgumentException("Entreprise introuvable"));

        if (isDefault) {
            eoriRepo.findByCompanyIdAndIsDefaultTrue(companyId)
                .ifPresent(e -> e.setDefault(false));
        }

        String effectiveHolderName = (holderName == null || holderName.isBlank()) ? online.traderName() : holderName;
        String effectiveAddress = (holderAddress == null || holderAddress.isBlank()) ? online.traderAddress() : holderAddress;

        EoriNumber eoriNumber = EoriNumber.builder()
            .company(company)
            .eori(eoriUpper)
            .holderName(effectiveHolderName)
            .holderAddress(effectiveAddress)
            .holderCountry(holderCountry != null ? holderCountry.toUpperCase() : null)
            .isDefault(isDefault)
            .isValid(isValid)
            .build();

        eoriRepo.save(eoriNumber);
        log.info("EORI {} créé pour company {}", eoriUpper, companyId);
        return eoriNumber;
    }

    public List<EoriNumber> list(UUID companyId) {
        return eoriRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    public Page<EoriNumber> list(UUID companyId, Pageable pageable) {
        return eoriRepo.findByCompanyIdOrderByCreatedAtDesc(companyId, pageable);
    }

    public EoriNumber getDefault(UUID companyId) {
        return eoriRepo.findByCompanyIdAndIsDefaultTrue(companyId)
            .orElseThrow(() -> new IllegalArgumentException("Aucun EORI par défaut configuré"));
    }

    @Transactional
    public EoriNumber setDefault(UUID companyId, UUID eoriId) {
        eoriRepo.findByCompanyIdAndIsDefaultTrue(companyId)
            .ifPresent(e -> e.setDefault(false));

        EoriNumber eori = eoriRepo.findById(eoriId)
            .orElseThrow(() -> new IllegalArgumentException("EORI introuvable"));

        if (!eori.getCompany().getId().equals(companyId)) {
            throw new IllegalArgumentException("Cet EORI n'appartient pas à cette entreprise");
        }

        eori.setDefault(true);
        eoriRepo.save(eori);
        return eori;
    }

    @Transactional
    public void delete(UUID companyId, UUID eoriId) {
        EoriNumber eori = eoriRepo.findById(eoriId)
            .orElseThrow(() -> new IllegalArgumentException("EORI introuvable"));

        if (!eori.getCompany().getId().equals(companyId)) {
            throw new IllegalArgumentException("Cet EORI n'appartient pas à cette entreprise");
        }

        eoriRepo.delete(eori);
        log.info("EORI {} supprimé pour company {}", eori.getEori(), companyId);
    }

    public Map<String, Object> validate(String eori) {
        String eoriUpper = eori.toUpperCase().replaceAll("\\s+", "");
        boolean valid = EU_EORI_PATTERN.matcher(eoriUpper).matches();
        String country = valid ? eoriUpper.substring(0, 2) : null;

        String message = valid ? "Format EORI valide" : "Format EORI invalide";

        if (valid) {
            EoriOnlineService.EoriCheck online = eoriOnlineService.checkEori(eoriUpper);
            if (online.message() == null) {
                valid = online.valid();
                message = online.valid()
                    ? "EORI valide (vérifié en ligne via le registre européen)"
                    : "EORI non valide (rejeté par le registre européen)";
            } else if (!online.message().contains("disabled")) {
                message += " — vérification en ligne indisponible";
            }
        }

        return Map.of(
            "eori", eoriUpper,
            "valid", valid,
            "country", country != null ? country : "",
            "message", message
        );
    }
}
