package com.incokalk.service;

import com.incokalk.model.ClientUser;
import com.incokalk.model.Company;
import com.incokalk.model.ShipmentOrder;
import com.incokalk.model.TrackingEvent;
import com.incokalk.repository.ClientUserRepository;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.ShipmentOrderRepository;
import com.incokalk.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClientAuthService {

    private final ClientUserRepository clientUserRepo;
    private final CompanyRepository companyRepo;
    private final ShipmentOrderRepository shipmentRepo;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder;

    public record AuthResult(String token, UUID clientId, String email, String fullName, UUID companyId) {}

    @Transactional
    public AuthResult login(String email, String password) {
        ClientUser client = clientUserRepo.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("Email ou mot de passe incorrect"));
        if (!client.isActive()) {
            throw new IllegalStateException("Compte client désactivé");
        }
        if (!passwordEncoder.matches(password, client.getPassword())) {
            throw new IllegalArgumentException("Email ou mot de passe incorrect");
        }
        client.setLastLoginAt(LocalDateTime.now());
        clientUserRepo.save(client);

        String token = jwtService.generateClientToken(client.getId(), client.getEmail(), client.getCompany().getId());
        return new AuthResult(token, client.getId(), client.getEmail(), client.getFullName(), client.getCompany().getId());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getProfile(UUID clientId) {
        ClientUser client = clientUserRepo.findById(clientId)
            .orElseThrow(() -> new RuntimeException("Client introuvable"));
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", client.getId());
        profile.put("email", client.getEmail());
        profile.put("fullName", client.getFullName());
        profile.put("phone", client.getPhone());
        profile.put("companyId", client.getCompany().getId());
        profile.put("companyName", client.getCompany().getName());
        return profile;
    }

    public List<ShipmentOrder> getMyShipments(UUID companyId, UUID clientId) {
        // Scopé par client : un client du portail ne voit QUE ses propres expéditions,
        // jamais celles des autres clients de la même entreprise.
        return shipmentRepo.findByCompanyIdAndClientIdOrderByCreatedAtDesc(companyId, clientId);
    }

    @Transactional(readOnly = true)
    public Optional<ShipmentOrder> getShipmentDetail(UUID companyId, UUID clientId, UUID shipmentId) {
        return shipmentRepo.findByIdAndCompanyIdAndClientId(shipmentId, companyId, clientId);
    }

    @Transactional(readOnly = true)
    public List<TrackingEvent> getShipmentTracking(UUID companyId, UUID clientId, UUID shipmentId) {
        return getShipmentDetail(companyId, clientId, shipmentId)
            .map(ShipmentOrder::getTrackingEvents)
            .orElse(List.of());
    }

    // ── Admin management ──────────────────────────────────────────────────

    public ClientUser createClient(UUID companyId, String email, String password, String fullName, String phone) {
        if (clientUserRepo.findByEmailAndCompanyId(email, companyId).isPresent()) {
            throw new RuntimeException("Un client avec cet email existe déjà pour cette entreprise");
        }
        Company company = companyRepo.findById(companyId)
            .orElseThrow(() -> new RuntimeException("Entreprise introuvable"));
        ClientUser client = ClientUser.builder()
            .company(company)
            .email(email)
            .password(passwordEncoder.encode(password))
            .fullName(fullName)
            .phone(phone)
            .active(true)
            .build();
        return clientUserRepo.save(client);
    }

    @Transactional
    public ClientUser updateClient(UUID clientId, UUID companyId, String fullName, String phone, Boolean active) {
        ClientUser client = clientUserRepo.findById(clientId)
            .filter(c -> c.getCompany().getId().equals(companyId))
            .orElseThrow(() -> new RuntimeException("Client introuvable"));
        if (fullName != null) client.setFullName(fullName);
        if (phone != null) client.setPhone(phone);
        if (active != null) client.setActive(active);
        return clientUserRepo.save(client);
    }

    @Transactional
    public void resetClientPassword(UUID clientId, UUID companyId, String newPassword) {
        ClientUser client = clientUserRepo.findById(clientId)
            .filter(c -> c.getCompany().getId().equals(companyId))
            .orElseThrow(() -> new RuntimeException("Client introuvable"));
        client.setPassword(passwordEncoder.encode(newPassword));
        clientUserRepo.save(client);
    }

    @Transactional
    public void deleteClient(UUID clientId, UUID companyId) {
        ClientUser client = clientUserRepo.findById(clientId)
            .filter(c -> c.getCompany().getId().equals(companyId))
            .orElseThrow(() -> new RuntimeException("Client introuvable"));
        clientUserRepo.delete(client);
    }

    public List<ClientUser> listClients(UUID companyId) {
        return clientUserRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    public Map<String, Object> clientStats(UUID companyId) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("totalClients", clientUserRepo.countByCompanyId(companyId));
        stats.put("activeClients", clientUserRepo.countByCompanyIdAndActive(companyId, true));
        return stats;
    }
}
