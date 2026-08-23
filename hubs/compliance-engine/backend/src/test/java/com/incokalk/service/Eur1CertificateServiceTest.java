package com.incokalk.service;

import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Company;
import com.incokalk.model.Eur1Certificate;
import com.incokalk.model.TradeAgreement;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.Eur1CertificateRepository;
import com.incokalk.repository.TradeAgreementRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Eur1CertificateService — Tests unitaires")
class Eur1CertificateServiceTest {

    Eur1CertificateRepository eur1Repo;
    CompanyRepository companyRepo;
    TradeAgreementRepository agreementRepo;
    Eur1CertificateService service;
    UUID companyId;
    UUID certId;
    Company company;
    Eur1Certificate cert;

    @BeforeEach
    void setUp() {
        eur1Repo = mock(Eur1CertificateRepository.class);
        companyRepo = mock(CompanyRepository.class);
        agreementRepo = mock(TradeAgreementRepository.class);
        service = new Eur1CertificateService(eur1Repo, companyRepo, agreementRepo);
        companyId = UUID.randomUUID();
        certId = UUID.randomUUID();
        company = Company.builder().id(companyId).build();
        cert = Eur1Certificate.builder()
                .id(certId).company(company).certificateNumber("EUR.1-20260728-0001")
                .agreementCode("DCFMA").originCountry("MA")
                .status(Eur1Certificate.CertificateStatus.ISSUED)
                .validUntil(LocalDate.now().plusMonths(6))
                .build();
    }

    @Test
    @DisplayName("create → succès")
    void create() {
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(agreementRepo.findByCode("DCFMA"))
                .thenReturn(Optional.of(TradeAgreement.builder().code("DCFMA").isActive(true).build()));
        when(eur1Repo.count()).thenReturn(0L);
        when(eur1Repo.save(any())).thenAnswer(i -> i.getArgument(0));

        Eur1Certificate dto = new Eur1Certificate();
        dto.setAgreementCode("DCFMA");
        dto.setOriginCountry("MA");
        dto.setImporterName("Client A");

        var result = service.create(companyId, dto);
        assertThat(result.getCertificateNumber()).startsWith("EUR.1-");
        assertThat(result.getStatus()).isEqualTo(Eur1Certificate.CertificateStatus.ISSUED);
    }

    @Test
    @DisplayName("create → société non trouvée")
    void create_companyNotFound() {
        when(companyRepo.findById(companyId)).thenReturn(Optional.empty());
        Eur1Certificate dto = new Eur1Certificate();
        assertThatThrownBy(() -> service.create(companyId, dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("create → accord non trouvé")
    void create_agreementNotFound() {
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(agreementRepo.findByCode("UNKNOWN")).thenReturn(Optional.empty());
        Eur1Certificate dto = new Eur1Certificate();
        dto.setAgreementCode("UNKNOWN");
        assertThatThrownBy(() -> service.create(companyId, dto))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("list → retourne certificats")
    void list() {
        when(eur1Repo.findByCompanyIdOrderByIssueDateDesc(companyId)).thenReturn(List.of(cert));
        assertThat(service.list(companyId)).hasSize(1);
    }

    @Test
    @DisplayName("get → trouvé")
    void get() {
        when(eur1Repo.findByCompanyIdAndId(companyId, certId)).thenReturn(Optional.of(cert));
        assertThat(service.get(companyId, certId)).isEqualTo(cert);
    }

    @Test
    @DisplayName("get → pas trouvé")
    void get_notFound() {
        when(eur1Repo.findByCompanyIdAndId(companyId, certId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(companyId, certId)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("delete → succès")
    void delete() {
        when(eur1Repo.findByCompanyIdAndId(companyId, certId)).thenReturn(Optional.of(cert));
        service.delete(companyId, certId);
        verify(eur1Repo).delete(cert);
    }

    @Test
    @DisplayName("validate → certificat valide")
    void validate_valid() {
        when(eur1Repo.findByCompanyIdAndId(companyId, certId)).thenReturn(Optional.of(cert));
        when(agreementRepo.findByCode("DCFMA"))
                .thenReturn(Optional.of(TradeAgreement.builder().code("DCFMA").isActive(true).build()));
        var result = service.validate(companyId, certId);
        assertThat(result.valid()).isTrue();
    }

    @Test
    @DisplayName("validate → expiré")
    void validate_expired() {
        cert.setValidUntil(LocalDate.now().minusDays(1));
        when(eur1Repo.findByCompanyIdAndId(companyId, certId)).thenReturn(Optional.of(cert));
        var result = service.validate(companyId, certId);
        assertThat(result.valid()).isFalse();
        assertThat(result.expired()).isTrue();
    }

    @Test
    @DisplayName("validate → accord inactif")
    void validate_agreementInactive() {
        when(eur1Repo.findByCompanyIdAndId(companyId, certId)).thenReturn(Optional.of(cert));
        when(agreementRepo.findByCode("DCFMA"))
                .thenReturn(Optional.of(TradeAgreement.builder().code("DCFMA").isActive(false).build()));
        var result = service.validate(companyId, certId);
        assertThat(result.valid()).isFalse();
        assertThat(result.agreementActive()).isFalse();
    }
}
