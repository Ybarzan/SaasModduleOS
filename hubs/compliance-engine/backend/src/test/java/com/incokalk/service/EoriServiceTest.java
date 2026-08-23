package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.EoriNumber;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.EoriNumberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("EoriService — Tests unitaires")
class EoriServiceTest {

    @Mock EoriNumberRepository eoriRepo;
    @Mock CompanyRepository companyRepo;
    @Mock EoriOnlineService eoriOnlineService;

    @InjectMocks EoriService service;

    private UUID companyId, eoriId;
    private Company company;
    private EoriNumber eoriNumber;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        companyId = UUID.randomUUID();
        eoriId = UUID.randomUUID();

        when(eoriOnlineService.checkEori(anyString()))
            .thenReturn(new EoriOnlineService.EoriCheck(false, null, null, "EORI online validation disabled"));

        company = Company.builder().id(companyId).name("TestCo").build();
        eoriNumber = EoriNumber.builder()
                .id(eoriId)
                .company(company)
                .eori("FR12345678900")
                .holderName("Test Holder")
                .holderCountry("FR")
                .isDefault(true)
                .isValid(true)
                .build();
    }

    // ── create ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Création d'EORI réussie")
    void create_success() {
        when(eoriRepo.existsByCompanyIdAndEori(companyId, "FR12345678900")).thenReturn(false);
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(eoriRepo.findByCompanyIdAndIsDefaultTrue(companyId)).thenReturn(Optional.empty());
        when(eoriRepo.save(any(EoriNumber.class))).thenAnswer(i -> i.getArgument(0));

        EoriNumber result = service.create(companyId, "FR12345678900", "Test Holder", null, "FR", true);

        assertThat(result).isNotNull();
        assertThat(result.getEori()).isEqualTo("FR12345678900");
        assertThat(result.getCompany()).isEqualTo(company);
        assertThat(result.isDefault()).isTrue();
    }

    @Test
    @DisplayName("Création d'EORI avec format invalide → exception")
    void create_invalidFormat_throws() {
        assertThatThrownBy(() -> service.create(companyId, "INVALID", "Test Holder", null, "FR", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Format EORI invalide");
    }

    @Test
    @DisplayName("Création d'EORI en double → exception")
    void create_duplicate_throws() {
        when(eoriRepo.existsByCompanyIdAndEori(companyId, "FR12345678900")).thenReturn(true);

        assertThatThrownBy(() -> service.create(companyId, "FR12345678900", "Test Holder", null, "FR", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cet EORI existe déjà");
    }

    @Test
    @DisplayName("Création d'EORI rejetée en ligne → exception")
    void create_onlineInvalid_throws() {
        when(eoriRepo.existsByCompanyIdAndEori(companyId, "FR12345678900")).thenReturn(false);
        when(eoriOnlineService.checkEori("FR12345678900"))
            .thenReturn(new EoriOnlineService.EoriCheck(false, null, null, null));

        assertThatThrownBy(() -> service.create(companyId, "FR12345678900", "Test Holder", null, "FR", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rejeté par le registre");
    }

    @Test
    @DisplayName("Création d'EORI validée en ligne → holder enrichi")
    void create_onlineValid_enrichesHolder() {
        when(eoriRepo.existsByCompanyIdAndEori(companyId, "FR12345678900")).thenReturn(false);
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(eoriOnlineService.checkEori("FR12345678900"))
            .thenReturn(new EoriOnlineService.EoriCheck(true, "Registre SA", "1 rue du Registre, 75001, Paris, FR", null));
        when(eoriRepo.save(any(EoriNumber.class))).thenAnswer(i -> i.getArgument(0));

        EoriNumber result = service.create(companyId, "FR12345678900", null, null, "FR", false);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getHolderName()).isEqualTo("Registre SA");
        assertThat(result.getHolderAddress()).isEqualTo("1 rue du Registre, 75001, Paris, FR");
    }

    @Test
    @DisplayName("Création d'EORI avec entreprise introuvable → exception")
    void create_companyNotFound_throws() {
        when(eoriRepo.existsByCompanyIdAndEori(companyId, "FR12345678900")).thenReturn(false);
        when(companyRepo.findById(companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(companyId, "FR12345678900", "Test Holder", null, "FR", false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Entreprise introuvable");
    }

    // ── list ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("Liste des EORI par entreprise")
    void list() {
        when(eoriRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(eoriNumber));

        List<EoriNumber> result = service.list(companyId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEori()).isEqualTo("FR12345678900");
    }

    // ── getDefault ────────────────────────────────────────────────────

    @Test
    @DisplayName("Récupération de l'EORI par défaut")
    void getDefault_success() {
        when(eoriRepo.findByCompanyIdAndIsDefaultTrue(companyId)).thenReturn(Optional.of(eoriNumber));

        EoriNumber result = service.getDefault(companyId);

        assertThat(result.isDefault()).isTrue();
        assertThat(result.getEori()).isEqualTo("FR12345678900");
    }

    @Test
    @DisplayName("Aucun EORI par défaut configuré → exception")
    void getDefault_notFound_throws() {
        when(eoriRepo.findByCompanyIdAndIsDefaultTrue(companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDefault(companyId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Aucun EORI par défaut configuré");
    }

    // ── setDefault ────────────────────────────────────────────────────

    @Test
    @DisplayName("Définir un EORI par défaut")
    void setDefault_success() {
        when(eoriRepo.findByCompanyIdAndIsDefaultTrue(companyId)).thenReturn(Optional.of(eoriNumber));
        when(eoriRepo.findById(eoriId)).thenReturn(Optional.of(eoriNumber));
        when(eoriRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        EoriNumber result = service.setDefault(companyId, eoriId);

        assertThat(result.isDefault()).isTrue();
    }

    @Test
    @DisplayName("EORI introuvable pour setDefault → exception")
    void setDefault_notFound_throws() {
        when(eoriRepo.findByCompanyIdAndIsDefaultTrue(companyId)).thenReturn(Optional.empty());
        when(eoriRepo.findById(eoriId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setDefault(companyId, eoriId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EORI introuvable");
    }

    @Test
    @DisplayName("EORI d'une autre entreprise pour setDefault → exception")
    void setDefault_wrongCompany_throws() {
        UUID otherCompanyId = UUID.randomUUID();
        EoriNumber otherEori = EoriNumber.builder()
                .id(eoriId)
                .company(Company.builder().id(otherCompanyId).name("Other").build())
                .eori("FR12345678900")
                .build();
        when(eoriRepo.findByCompanyIdAndIsDefaultTrue(companyId)).thenReturn(Optional.empty());
        when(eoriRepo.findById(eoriId)).thenReturn(Optional.of(otherEori));

        assertThatThrownBy(() -> service.setDefault(companyId, eoriId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("n'appartient pas");
    }

    // ── delete ────────────────────────────────────────────────────────

    @Test
    @DisplayName("Suppression d'EORI réussie")
    void delete_success() {
        when(eoriRepo.findById(eoriId)).thenReturn(Optional.of(eoriNumber));

        service.delete(companyId, eoriId);

        verify(eoriRepo).delete(eoriNumber);
    }

    @Test
    @DisplayName("Suppression d'EORI introuvable → exception")
    void delete_notFound_throws() {
        when(eoriRepo.findById(eoriId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(companyId, eoriId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("EORI introuvable");

        verify(eoriRepo, never()).delete(any());
    }

    @Test
    @DisplayName("Suppression d'EORI d'une autre entreprise → exception")
    void delete_wrongCompany_throws() {
        UUID otherCompanyId = UUID.randomUUID();
        EoriNumber otherEori = EoriNumber.builder()
                .id(eoriId)
                .company(Company.builder().id(otherCompanyId).name("Other").build())
                .eori("FR12345678900")
                .build();
        when(eoriRepo.findById(eoriId)).thenReturn(Optional.of(otherEori));

        assertThatThrownBy(() -> service.delete(companyId, eoriId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("n'appartient pas");

        verify(eoriRepo, never()).delete(any());
    }

    // ── validate ──────────────────────────────────────────────────────

    @Test
    @DisplayName("Validation d'EORI FR valide")
    void validate_valid() {
        Map<String, Object> result = service.validate("FR12345678900");

        assertThat(result.get("valid")).isEqualTo(true);
        assertThat(result.get("eori")).isEqualTo("FR12345678900");
        assertThat(result.get("country")).isEqualTo("FR");
        assertThat((String) result.get("message")).contains("valide");
    }

    @Test
    @DisplayName("Validation d'EORI avec format invalide")
    void validate_invalidFormat() {
        Map<String, Object> result = service.validate("INVALID");

        assertThat(result.get("valid")).isEqualTo(false);
        assertThat(result.get("country")).isEqualTo("");
        assertThat((String) result.get("message")).contains("invalide");
    }

    @Test
    @DisplayName("Validation d'EORI avec mauvais préfixe")
    void validate_wrongPrefix() {
        Map<String, Object> result = service.validate("12345678900");

        assertThat(result.get("valid")).isEqualTo(false);
    }
}
