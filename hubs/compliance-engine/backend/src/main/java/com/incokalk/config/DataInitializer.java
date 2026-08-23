package com.incokalk.config;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.User;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.CompanyRoleRepository;
import com.incokalk.repository.UserRepository;
import com.incokalk.service.CompanyService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepo;
    private final CompanyRepository companyRepo;
    private final CompanyRoleRepository companyRoleRepo;
    private final CompanyService companyService;
    private final BCryptPasswordEncoder encoder;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public void run(String... args) {
        if (userRepo.existsByEmail("admin@incokalk.io")) {
            log.info("Seed déjà présent, skip.");
            return;
        }

        String adminPassword = randomPassword();
        User admin = User.builder()
            .email("admin@incokalk.io")
            .password(encoder.encode(adminPassword))
            .fullName("Admin IncoKalk")
            .plan(User.Plan.FREE)
            .emailVerified(true)
            .active(true)
            .build();
        entityManager.persist(admin);
        entityManager.flush();

        String slug = companyService.generateUniqueSlug("IncoKalk Demo");
        Company company = Company.builder()
            .name("IncoKalk Demo")
            .slug(slug)
            .plan(Company.Plan.FREE)
            .build();
        entityManager.persist(company);
        entityManager.flush();

        CompanyRole role = new CompanyRole();
        role.setCompany(company);
        role.setUser(admin);
        role.setRole(CompanyRole.Role.OWNER);
        entityManager.persist(role);
        entityManager.flush();

        admin.setCompany(company);
        entityManager.merge(admin);

        log.info("═══════════════════════════════════════════════════");
        log.info("  Admin seed créé avec succès !");
        log.info("  Email    : admin@incokalk.io");
        log.info("  Password : {}", adminPassword);
        log.info("  (Changer le mot de passe immédiatement après la 1ère connexion)");
        log.info("  Company  : IncoKalk Demo");
        log.info("═══════════════════════════════════════════════════");
    }

    private static String randomPassword() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghijkmnpqrstuvwxyz";
        String digits = "23456789";
        String special = "!@#$%^&*";
        String all = upper + lower + digits + special;
        java.security.SecureRandom rnd = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(16);
        sb.append(upper.charAt(rnd.nextInt(upper.length())));
        sb.append(lower.charAt(rnd.nextInt(lower.length())));
        sb.append(digits.charAt(rnd.nextInt(digits.length())));
        sb.append(special.charAt(rnd.nextInt(special.length())));
        for (int i = 4; i < 16; i++) {
            sb.append(all.charAt(rnd.nextInt(all.length())));
        }
        return sb.toString();
    }
}
