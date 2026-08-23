package com.fleethub.service;

import com.fleethub.model.AppUser;
import com.fleethub.model.Company;
import com.fleethub.repository.AppUserRepository;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.service.email.EmailNotifier;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Rappelle aux sociétés en TRIAL que leur essai gratuit expire (J-7 puis J-1),
 * par email aux administrateurs de la société.
 */
@Component
@RequiredArgsConstructor
public class TrialExpiryReminderTask {

    private static final Logger log = LoggerFactory.getLogger(TrialExpiryReminderTask.class);

    private final CompanyRepository companyRepository;
    private final AppUserRepository userRepository;
    private final EmailNotifier emailNotifier;
    private final Clock clock;

    @Scheduled(cron = "0 30 6 * * *")
    @Transactional
    public void remind() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<Company> trials = companyRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(c -> c.getStatus() == Company.CompanyStatus.TRIAL)
                .filter(c -> c.getTrialEndsAt() != null)
                .toList();

        int sent = 0;
        for (Company company : trials) {
            long daysLeft = ChronoUnit.DAYS.between(now, company.getTrialEndsAt());
            boolean j7 = daysLeft >= 6 && daysLeft <= 8;
            boolean j1 = daysLeft == 1;
            if (!j7 && !j1) continue;

            LocalDateTime lastReminder = company.getLastTrialReminderAt();
            boolean alreadySent = lastReminder != null
                    && lastReminder.isAfter(company.getTrialEndsAt().minusDays(j7 ? 7 : 1));
            if (alreadySent) continue;

            List<AppUser> admins = userRepository.findByCompanyId(company.getId()).stream()
                    .filter(u -> "ADMIN".equals(u.getRole()))
                    .filter(AppUser::isEnabled)
                    .filter(u -> u.getEmail() != null && !u.getEmail().isBlank())
                    .toList();
            for (AppUser admin : admins) {
                emailNotifier.trialExpiring(admin.getEmail(), company.getName(), company.getTrialEndsAt());
            }
            company.setLastTrialReminderAt(now);
            companyRepository.save(company);
            sent += Math.max(1, admins.size());
        }
        if (sent > 0) {
            log.info("Rappels d'expiration d'essai envoyés ({})", sent);
        }
    }
}
