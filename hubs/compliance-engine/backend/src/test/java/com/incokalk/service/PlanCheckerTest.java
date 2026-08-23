package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("PlanChecker — Tests unitaires")
class PlanCheckerTest {

    CompanyRepository companyRepo;
    PlanChecker checker;
    UUID companyId;

    @BeforeEach
    void setUp() {
        companyRepo = mock(CompanyRepository.class);
        checker = new PlanChecker(companyRepo);
        companyId = UUID.randomUUID();
    }

    @Test
    @DisplayName("hasMinimumPlan → ENTERPRISE satisfait ENTERPRISE")
    void hasMinimumPlan_enterpriseMeetsEnterprise() {
        Company c = Company.builder().plan(Company.Plan.ENTERPRISE).build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(c));
        assertThat(checker.hasMinimumPlan(companyId, Company.Plan.ENTERPRISE)).isTrue();
    }

    @Test
    @DisplayName("hasMinimumPlan → STARTER ne satisfait pas PRO")
    void hasMinimumPlan_starterNotPro() {
        Company c = Company.builder().plan(Company.Plan.STARTER).build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(c));
        assertThat(checker.hasMinimumPlan(companyId, Company.Plan.PRO)).isFalse();
    }

    @Test
    @DisplayName("hasMinimumPlan → PRO satisfait STARTER (palier superieur couvre l'inferieur)")
    void hasMinimumPlan_proMeetsStarter() {
        Company c = Company.builder().plan(Company.Plan.PRO).build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(c));
        assertThat(checker.hasMinimumPlan(companyId, Company.Plan.STARTER)).isTrue();
    }

    @Test
    @DisplayName("hasMinimumPlan → FREE ne satisfait pas STARTER")
    void hasMinimumPlan_freeNotStarter() {
        Company c = Company.builder().plan(Company.Plan.FREE).build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(c));
        assertThat(checker.hasMinimumPlan(companyId, Company.Plan.STARTER)).isFalse();
    }

    @Test
    @DisplayName("hasMinimumPlan → entreprise introuvable → false")
    void hasMinimumPlan_companyNotFound() {
        when(companyRepo.findById(companyId)).thenReturn(Optional.empty());
        assertThat(checker.hasMinimumPlan(companyId, Company.Plan.FREE)).isFalse();
    }
}
