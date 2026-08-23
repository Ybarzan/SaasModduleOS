package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.SharedLink;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.model.User;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.SharedLinkRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import com.incokalk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class SharedLinkService {

    private final SharedLinkRepository sharedLinkRepo;
    private final ShipmentOrderRepository shipmentRepo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;

    @Transactional
    public SharedLink createLink(UUID companyId, UUID shipmentId, UUID userId, String label, Integer expiresHours) {
        ShipmentOrder shipment = shipmentRepo.findById(shipmentId)
            .filter(s -> s.getCompany().getId().equals(companyId))
            .orElseThrow(() -> new RuntimeException("Expédition introuvable"));
        Company company = companyRepo.findById(companyId)
            .orElseThrow(() -> new RuntimeException("Entreprise introuvable"));
        User user = userRepo.findById(userId).orElse(null);

        SharedLink link = SharedLink.builder()
            .company(company)
            .shipment(shipment)
            .token(UUID.randomUUID().toString())
            .createdBy(user)
            .label(label != null ? label : "Lien de suivi - " + shipment.getOrderNumber())
            .expiresAt(expiresHours != null ? LocalDateTime.now().plusHours(expiresHours) : null)
            .active(true)
            .build();
        return sharedLinkRepo.save(link);
    }

    public List<SharedLink> listLinks(UUID companyId) {
        return sharedLinkRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    public List<SharedLink> listLinksForShipment(UUID shipmentId, UUID companyId) {
        return sharedLinkRepo.findByShipmentIdAndCompanyIdOrderByCreatedAtDesc(shipmentId, companyId);
    }

    @Transactional
    public SharedLink accessLink(String token) {
        SharedLink link = sharedLinkRepo.findByToken(token)
            .orElseThrow(() -> new RuntimeException("Lien introuvable ou invalide"));
        if (!link.isActive()) {
            throw new RuntimeException("Ce lien a été révoqué");
        }
        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Ce lien a expiré");
        }
        link.setAccessCount(link.getAccessCount() + 1);
        link.setLastAccessedAt(LocalDateTime.now());
        sharedLinkRepo.save(link);
        return link;
    }

    @Transactional
    public void revokeLink(UUID linkId, UUID companyId) {
        SharedLink link = sharedLinkRepo.findById(linkId)
            .filter(l -> l.getCompany().getId().equals(companyId))
            .orElseThrow(() -> new RuntimeException("Lien introuvable"));
        link.setActive(false);
        sharedLinkRepo.save(link);
    }

    @Transactional
    public void deleteLink(UUID linkId, UUID companyId) {
        SharedLink link = sharedLinkRepo.findById(linkId)
            .filter(l -> l.getCompany().getId().equals(companyId))
            .orElseThrow(() -> new RuntimeException("Lien introuvable"));
        sharedLinkRepo.delete(link);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> linkStats(UUID companyId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalLinks", sharedLinkRepo.countByCompanyId(companyId));
        List<SharedLink> all = sharedLinkRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
        stats.put("activeLinks", all.stream().filter(SharedLink::isActive).count());
        stats.put("totalAccesses", all.stream().mapToInt(SharedLink::getAccessCount).sum());
        return stats;
    }
}
