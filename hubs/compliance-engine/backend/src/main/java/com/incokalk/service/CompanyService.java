package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.User;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.CompanyRoleRepository;
import com.incokalk.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private static final String REFERRAL_CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sans I/O/0/1, ambigus à l'oreille/à l'écrit
    private static final int REFERRAL_CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final CompanyRepository companyRepo;
    private final CompanyRoleRepository roleRepo;
    private final UserRepository userRepo;

    @Transactional
    public Company createCompany(String name, String slug, UUID ownerId) {
        Company company = Company.builder()
            .name(name)
            .slug(slug)
            .plan(Company.Plan.FREE)
            .referralCode(generateUniqueReferralCode())
            .build();
        company = companyRepo.save(company);

        roleRepo.save(CompanyRole.builder()
            .company(company)
            .user(userRepo.findById(ownerId).orElseThrow())
            .role(CompanyRole.Role.OWNER)
            .build());

        return company;
    }

    public String generateUniqueReferralCode() {
        String code;
        do {
            StringBuilder sb = new StringBuilder(REFERRAL_CODE_LENGTH);
            for (int i = 0; i < REFERRAL_CODE_LENGTH; i++) {
                sb.append(REFERRAL_CODE_ALPHABET.charAt(RANDOM.nextInt(REFERRAL_CODE_ALPHABET.length())));
            }
            code = sb.toString();
        } while (companyRepo.existsByReferralCode(code));
        return code;
    }

    /** Backfill paresseux : les sociétés créées avant ce champ n'ont pas de code
     * tant qu'elles n'en demandent pas un. */
    @Transactional
    public String getOrCreateReferralCode(UUID companyId) {
        Company company = companyRepo.findById(companyId)
            .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
        if (company.getReferralCode() == null) {
            company.setReferralCode(generateUniqueReferralCode());
            companyRepo.save(company);
        }
        return company.getReferralCode();
    }

    public Optional<Company> findByReferralCode(String referralCode) {
        return companyRepo.findByReferralCode(referralCode);
    }

    @Transactional
    public void setReferredBy(UUID companyId, UUID referrerCompanyId) {
        companyRepo.findById(companyId).ifPresent(company -> {
            company.setReferredByCompanyId(referrerCompanyId);
            companyRepo.save(company);
        });
    }

    public boolean slugExists(String slug) {
        return companyRepo.existsBySlug(slug);
    }

    public String generateUniqueSlug(String name) {
        String base = name.toLowerCase()
            .replaceAll("[^a-z0-9]", "-")
            .replaceAll("-+", "-")
            .replaceAll("^-|-$", "");
        String slug = base;
        int i = 1;
        while (companyRepo.existsBySlug(slug)) {
            slug = base + "-" + i++;
        }
        return slug;
    }

    public Optional<Company> findById(UUID id) {
        return companyRepo.findById(id);
    }

    public Company getReferenceById(UUID id) {
        return companyRepo.getReferenceById(id);
    }

    @Transactional
    public Company updateCompany(UUID id, String name, String slug, String logoUrl) {
        Company company = companyRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Company not found"));
        if (name != null) company.setName(name);
        if (slug != null) company.setSlug(slug);
        if (logoUrl != null) company.setLogoUrl(logoUrl);
        return companyRepo.save(company);
    }

    public Optional<User> findUserByEmail(String email) {
        return userRepo.findByEmail(email);
    }

    public boolean existsById(UUID id) {
        return companyRepo.existsById(id);
    }

    public List<CompanyRole> findRolesByCompanyId(UUID companyId) {
        return roleRepo.findByCompanyId(companyId);
    }

    public Optional<CompanyRole> findRoleByCompanyIdAndUserId(UUID companyId, UUID userId) {
        return roleRepo.findByCompanyIdAndUserId(companyId, userId);
    }

    @Transactional
    public CompanyRole saveRole(CompanyRole role) {
        return roleRepo.save(role);
    }

    @Transactional
    public void deleteRole(CompanyRole role) {
        roleRepo.delete(role);
    }
}
