package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.User;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.CompanyRoleRepository;
import com.incokalk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CompanyService — Tests unitaires")
class CompanyServiceTest {

    CompanyRepository companyRepo;
    CompanyRoleRepository roleRepo;
    UserRepository userRepo;
    CompanyService service;
    UUID ownerId;

    @BeforeEach
    void setUp() {
        companyRepo = mock(CompanyRepository.class);
        roleRepo = mock(CompanyRoleRepository.class);
        userRepo = mock(UserRepository.class);
        service = new CompanyService(companyRepo, roleRepo, userRepo);
        ownerId = UUID.randomUUID();
    }

    @Test
    @DisplayName("createCompany → sauvegarde company + role OWNER")
    void createCompany() {
        when(companyRepo.save(any())).thenAnswer(i -> {
            Company c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(userRepo.findById(ownerId)).thenReturn(Optional.of(User.builder().id(ownerId).build()));
        when(roleRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Company result = service.createCompany("Acme", "acme", ownerId);
        assertThat(result.getName()).isEqualTo("Acme");
        assertThat(result.getSlug()).isEqualTo("acme");
        verify(roleRepo).save(any());
    }

    @Test
    @DisplayName("slugExists → true si pris")
    void slugExists_true() {
        when(companyRepo.existsBySlug("acme")).thenReturn(true);
        assertThat(service.slugExists("acme")).isTrue();
    }

    @Test
    @DisplayName("slugExists → false si libre")
    void slugExists_false() {
        when(companyRepo.existsBySlug("acme")).thenReturn(false);
        assertThat(service.slugExists("acme")).isFalse();
    }

    @Test
    @DisplayName("generateUniqueSlug → slug simple")
    void generateUniqueSlug_simple() {
        when(companyRepo.existsBySlug("acme-corp")).thenReturn(false);
        assertThat(service.generateUniqueSlug("Acme Corp!")).isEqualTo("acme-corp");
    }

    @Test
    @DisplayName("generateUniqueSlug → slug pris → incrémente")
    void generateUniqueSlug_increment() {
        when(companyRepo.existsBySlug("acme")).thenReturn(true);
        when(companyRepo.existsBySlug("acme-1")).thenReturn(false);
        assertThat(service.generateUniqueSlug("Acme!")).isEqualTo("acme-1");
    }

    @Test
    @DisplayName("createCompany → génère un code de parrainage unique")
    void createCompany_generatesReferralCode() {
        when(companyRepo.existsByReferralCode(any())).thenReturn(false);
        when(companyRepo.save(any())).thenAnswer(i -> {
            Company c = i.getArgument(0);
            c.setId(UUID.randomUUID());
            return c;
        });
        when(userRepo.findById(ownerId)).thenReturn(Optional.of(User.builder().id(ownerId).build()));
        when(roleRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Company result = service.createCompany("Acme", "acme", ownerId);
        assertThat(result.getReferralCode()).hasSize(8);
    }

    @Test
    @DisplayName("generateUniqueReferralCode → retire si code déjà pris")
    void generateUniqueReferralCode_retriesOnCollision() {
        when(companyRepo.existsByReferralCode(any()))
            .thenReturn(true)
            .thenReturn(false);

        String code = service.generateUniqueReferralCode();
        assertThat(code).hasSize(8);
        verify(companyRepo, times(2)).existsByReferralCode(any());
    }

    @Test
    @DisplayName("getOrCreateReferralCode → réutilise le code existant sans le régénérer")
    void getOrCreateReferralCode_reusesExisting() {
        UUID companyId = UUID.randomUUID();
        Company company = Company.builder().id(companyId).referralCode("EXISTING1").build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

        String code = service.getOrCreateReferralCode(companyId);
        assertThat(code).isEqualTo("EXISTING1");
        verify(companyRepo, never()).save(any());
    }

    @Test
    @DisplayName("getOrCreateReferralCode → backfill paresseux si absent")
    void getOrCreateReferralCode_backfillsWhenMissing() {
        UUID companyId = UUID.randomUUID();
        Company company = Company.builder().id(companyId).referralCode(null).build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(companyRepo.existsByReferralCode(any())).thenReturn(false);
        when(companyRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        String code = service.getOrCreateReferralCode(companyId);
        assertThat(code).hasSize(8);
        verify(companyRepo).save(company);
    }

    @Test
    @DisplayName("findByReferralCode → délègue au repository")
    void findByReferralCode_delegates() {
        Company company = Company.builder().id(UUID.randomUUID()).build();
        when(companyRepo.findByReferralCode("ABCD1234")).thenReturn(Optional.of(company));

        assertThat(service.findByReferralCode("ABCD1234")).contains(company);
    }

    @Test
    @DisplayName("setReferredBy → lie la société parrainée au parrain")
    void setReferredBy_linksCompany() {
        UUID companyId = UUID.randomUUID();
        UUID referrerId = UUID.randomUUID();
        Company company = Company.builder().id(companyId).build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

        service.setReferredBy(companyId, referrerId);

        assertThat(company.getReferredByCompanyId()).isEqualTo(referrerId);
        verify(companyRepo).save(company);
    }

    @Test
    @DisplayName("setReferredBy → no-op si société introuvable")
    void setReferredBy_noopWhenCompanyMissing() {
        UUID companyId = UUID.randomUUID();
        when(companyRepo.findById(companyId)).thenReturn(Optional.empty());

        service.setReferredBy(companyId, UUID.randomUUID());

        verify(companyRepo, never()).save(any());
    }
}
