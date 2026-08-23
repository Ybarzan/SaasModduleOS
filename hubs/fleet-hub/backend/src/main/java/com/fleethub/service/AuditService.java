package com.fleethub.service;

import com.fleethub.model.AppUser;
import com.fleethub.model.AuditLog;
import com.fleethub.model.Company;
import com.fleethub.repository.AppUserRepository;
import com.fleethub.repository.AuditLogRepository;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.security.AppUserPrincipal;
import com.fleethub.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Journal d'audit (RGPD, art. 5.2 « responsabilité ») : persiste une trace de
 * chaque événement sensible (connexion, invitation, suppression, export, …).
 * La trace rejoint la transaction courante : elle voit donc les données
 * fraîchement créées (ex. société à l'inscription) et est annulée avec
 * l'opération en cas de rollback.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final CompanyRepository companyRepository;
    private final AppUserRepository userRepository;

    /**
     * Consigne un événement pour le tenant courant, avec l'utilisateur
     * authentifié de la requête en cours (SecurityContext).
     */
    @Transactional
    public void log(String action, String detail) {
        Company company = TenantContext.get();
        Long userId = null;
        String username = null;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AppUserPrincipal principal) {
            userId = principal.getId();
            username = principal.getUsername();
        }
        save(company != null ? company.getId() : null, userId, username, action, detail, null);
    }

    /**
     * Consigne un événement en précisant explicitement le tenant et l'auteur
     * (cas des flux sans requête HTTP : webhooks, jobs planifiés, login).
     */
    @Transactional
    public void log(Long companyId, Long userId, String username, String action, String detail, String ipAddress) {
        save(companyId, userId, username, action, detail, ipAddress);
    }

    /** Consigne un événement pour l'utilisateur fourni (ex. login réussi). */
    @Transactional
    public void logForUser(AppUser user, String action, String detail, String ipAddress) {
        Long companyId = user.getCompany() != null ? user.getCompany().getId() : null;
        save(companyId, user.getId(), user.getUsername(), action, detail, ipAddress);
    }

    /** Consigne un événement en retrouvant l'identifiant utilisateur par email. */
    @Transactional
    public void logForUsername(Long companyId, String username, String action, String detail, String ipAddress) {
        Long userId = userRepository.findByUsername(username).map(AppUser::getId).orElse(null);
        save(companyId, userId, username, action, detail, ipAddress);
    }

    private void save(Long companyId, Long userId, String username, String action, String detail, String ipAddress) {
        AuditLog entry = new AuditLog();
        if (companyId != null) {
            entry.setCompany(companyRepository.getReferenceById(companyId));
        }
        entry.setUserId(userId);
        entry.setUsername(username);
        entry.setAction(action);
        entry.setDetail(detail != null && detail.length() > 2000 ? detail.substring(0, 2000) : detail);
        entry.setIpAddress(ipAddress);
        entry.setCreatedAt(LocalDateTime.now());
        auditLogRepository.save(entry);
    }
}
