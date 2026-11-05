package com.fleethub.service;

import com.fleethub.model.AppUser;
import com.fleethub.model.Company;
import com.fleethub.model.SharedLink;
import com.fleethub.model.ShipmentOrder;
import com.fleethub.repository.AppUserRepository;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.repository.SharedLinkRepository;
import com.fleethub.repository.ShipmentOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

/** Liens de suivi publics (sans authentification), par token. Porté depuis compliance-engine. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SharedLinkService {

    /** Un lien public n'est jamais permanent : 30 jours par défaut, 1 an au plus. */
    public static final int DEFAULT_EXPIRY_HOURS = 30 * 24;
    public static final int MAX_EXPIRY_HOURS = 365 * 24;

    private final SharedLinkRepository sharedLinkRepo;
    private final ShipmentOrderRepository shipmentRepo;
    private final CompanyRepository companyRepo;
    private final AppUserRepository userRepo;

    @Transactional
    public SharedLink createLink(Long companyId, UUID shipmentId, Long userId, String label, Integer expiresHours) {
        ShipmentOrder shipment = shipmentRepo.findById(shipmentId)
            .filter(s -> s.getCompany().getId().equals(companyId))
            .orElseThrow(() -> new RuntimeException("Expédition introuvable"));
        Company company = companyRepo.findById(companyId)
            .orElseThrow(() -> new RuntimeException("Entreprise introuvable"));
        AppUser user = userId != null ? userRepo.findById(userId).orElse(null) : null;

        SharedLink link = SharedLink.builder()
            .company(company)
            .shipment(shipment)
            .token(UUID.randomUUID().toString())
            .createdBy(user)
            .label(label != null ? label : "Lien de suivi - " + shipment.getOrderNumber())
            .expiresAt(LocalDateTime.now().plusHours(expiresHours != null ? expiresHours : DEFAULT_EXPIRY_HOURS))
            .active(true)
            .build();
        return sharedLinkRepo.save(link);
    }

    public List<SharedLink> listLinks(Long companyId) {
        return sharedLinkRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    public List<SharedLink> listLinksForShipment(UUID shipmentId, Long companyId) {
        return sharedLinkRepo.findByShipmentIdAndCompanyIdOrderByCreatedAtDesc(shipmentId, companyId);
    }

    @Transactional
    public SharedLink accessLink(String token) {
        SharedLink link = sharedLinkRepo.findByToken(token)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lien introuvable ou invalide"));
        if (!link.isActive()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Ce lien a été révoqué");
        }
        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.GONE, "Ce lien a expiré");
        }
        link.setAccessCount(link.getAccessCount() + 1);
        link.setLastAccessedAt(LocalDateTime.now());
        sharedLinkRepo.save(link);
        return link;
    }

    @Transactional
    public void revokeLink(UUID linkId, Long companyId) {
        SharedLink link = sharedLinkRepo.findById(linkId)
            .filter(l -> l.getCompany().getId().equals(companyId))
            .orElseThrow(() -> new RuntimeException("Lien introuvable"));
        link.setActive(false);
        sharedLinkRepo.save(link);
    }

    @Transactional
    public void deleteLink(UUID linkId, Long companyId) {
        SharedLink link = sharedLinkRepo.findById(linkId)
            .filter(l -> l.getCompany().getId().equals(companyId))
            .orElseThrow(() -> new RuntimeException("Lien introuvable"));
        sharedLinkRepo.delete(link);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> linkStats(Long companyId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalLinks", sharedLinkRepo.countByCompanyId(companyId));
        List<SharedLink> all = sharedLinkRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
        stats.put("activeLinks", all.stream().filter(SharedLink::isActive).count());
        stats.put("totalAccesses", all.stream().mapToInt(SharedLink::getAccessCount).sum());
        return stats;
    }
}
