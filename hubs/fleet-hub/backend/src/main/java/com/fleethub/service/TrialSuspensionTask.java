package com.fleethub.service;

import com.fleethub.model.Company;
import com.fleethub.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Bascule en {@code SUSPENDED} les sociétés dont l'essai gratuit est expiré
 * depuis plus de {@code app.trial.grace-days} jours. L'accès aux données est
 * déjà gelé à l'expiration par {@code SubscriptionGuardFilter} ; cette tâche
 * assainit simplement le statut affiché (visibilité pour l'opérateur SaaS).
 */
@Component
@RequiredArgsConstructor
public class TrialSuspensionTask {

    private static final Logger log = LoggerFactory.getLogger(TrialSuspensionTask.class);

    private final CompanyRepository companyRepository;

    @Value("${app.trial.grace-days:7}")
    private int graceDays;

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void suspendExpiredTrials() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(graceDays);
        List<Company> expired = companyRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(c -> c.getStatus() == Company.CompanyStatus.TRIAL)
                .filter(c -> c.getTrialEndsAt() != null)
                .filter(c -> c.getTrialEndsAt().isBefore(cutoff))
                .toList();

        if (expired.isEmpty()) {
            return;
        }
        for (Company company : expired) {
            company.setStatus(Company.CompanyStatus.SUSPENDED);
            companyRepository.save(company);
        }
        log.info("Essais expirés suspendus ({} société(s), grâce de {} jour(s))",
                expired.size(), graceDays);
    }
}
