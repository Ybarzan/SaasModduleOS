package com.incokalk.service;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyBranding;
import com.incokalk.repository.CompanyBrandingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("BrandingService — Tests unitaires")
class BrandingServiceTest {

    BrandingService service;
    CompanyBrandingRepository brandingRepo;

    @BeforeEach
    void setUp() {
        brandingRepo = mock(CompanyBrandingRepository.class);
        service = new BrandingService(brandingRepo);
    }

    @Test
    @DisplayName("getBranding → trouvé")
    void getBranding_found() {
        UUID companyId = UUID.randomUUID();
        CompanyBranding branding = CompanyBranding.builder()
                .company(Company.builder().id(companyId).build())
                .primaryColor("#FF0000")
                .build();
        when(brandingRepo.findByCompanyId(companyId)).thenReturn(Optional.of(branding));

        CompanyBranding result = service.getBranding(companyId);

        assertThat(result).isNotNull();
        assertThat(result.getPrimaryColor()).isEqualTo("#FF0000");
    }

    @Test
    @DisplayName("getBranding → non trouvé → null")
    void getBranding_notFound() {
        UUID companyId = UUID.randomUUID();
        when(brandingRepo.findByCompanyId(companyId)).thenReturn(Optional.empty());

        assertThat(service.getBranding(companyId)).isNull();
    }

    @Test
    @DisplayName("getBrandingByDomain → trouvé")
    void getBrandingByDomain_found() {
        String domain = "portal.example.com";
        CompanyBranding branding = CompanyBranding.builder()
                .customDomain(domain)
                .portalTitle("My Portal")
                .build();
        when(brandingRepo.findByCustomDomain(domain)).thenReturn(Optional.of(branding));

        CompanyBranding result = service.getBrandingByDomain(domain);

        assertThat(result).isNotNull();
        assertThat(result.getPortalTitle()).isEqualTo("My Portal");
    }

    @Test
    @DisplayName("getBrandingByDomain → non trouvé → null")
    void getBrandingByDomain_notFound() {
        when(brandingRepo.findByCustomDomain("unknown.com")).thenReturn(Optional.empty());

        assertThat(service.getBrandingByDomain("unknown.com")).isNull();
    }

    @Test
    @DisplayName("saveBranding → persiste et retourne")
    void saveBranding() {
        CompanyBranding branding = CompanyBranding.builder()
                .primaryColor("#00FF00")
                .build();
        when(brandingRepo.save(branding)).thenReturn(branding);

        CompanyBranding result = service.saveBranding(branding);

        assertThat(result.getPrimaryColor()).isEqualTo("#00FF00");
        verify(brandingRepo).save(branding);
    }

    @Test
    @DisplayName("updateBranding → existant → met à jour")
    void updateBranding_existing() {
        UUID companyId = UUID.randomUUID();
        CompanyBranding existing = CompanyBranding.builder()
                .company(Company.builder().id(companyId).build())
                .primaryColor("#2563EB")
                .build();
        when(brandingRepo.findByCompanyId(companyId)).thenReturn(Optional.of(existing));
        when(brandingRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> updates = Map.of("primaryColor", "#FF0000", "portalTitle", "New Title");
        CompanyBranding result = service.updateBranding(companyId, updates);

        assertThat(result.getPrimaryColor()).isEqualTo("#FF0000");
        assertThat(result.getPortalTitle()).isEqualTo("New Title");
    }

    @Test
    @DisplayName("updateBranding → inexistant → crée nouveau")
    void updateBranding_createsNew() {
        UUID companyId = UUID.randomUUID();
        when(brandingRepo.findByCompanyId(companyId)).thenReturn(Optional.empty());
        when(brandingRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        Map<String, Object> updates = Map.of("primaryColor", "#333333");
        CompanyBranding result = service.updateBranding(companyId, updates);

        assertThat(result.getPrimaryColor()).isEqualTo("#333333");
        verify(brandingRepo, times(2)).save(any());
    }

    @Test
    @DisplayName("getTranslations → FR par défaut")
    void getTranslations_default() {
        Map<String, String> fr = service.getTranslations("FR");
        assertThat(fr).containsEntry("nav.home", "Accueil");
    }

    @Test
    @DisplayName("getTranslations → EN")
    void getTranslations_en() {
        Map<String, String> en = service.getTranslations("EN");
        assertThat(en).containsEntry("nav.home", "Home");
    }

    @Test
    @DisplayName("getTranslations → null → retourne FR")
    void getTranslations_null() {
        Map<String, String> result = service.getTranslations(null);
        assertThat(result).containsEntry("nav.home", "Accueil");
    }

    @Test
    @DisplayName("getTranslations → langue inconnue → fallback FR")
    void getTranslations_fallback() {
        Map<String, String> result = service.getTranslations("IT");
        assertThat(result).containsEntry("nav.home", "Accueil");
    }

    @Test
    @DisplayName("getPortalConfig → avec branding")
    void getPortalConfig_withBranding() {
        UUID companyId = UUID.randomUUID();
        CompanyBranding branding = CompanyBranding.builder()
                .company(Company.builder().id(companyId).build())
                .primaryColor("#123456")
                .portalTitle("Custom Portal")
                .build();
        when(brandingRepo.findByCompanyId(companyId)).thenReturn(Optional.of(branding));

        Map<String, Object> config = service.getPortalConfig(companyId, "EN");

        assertThat(config).containsKey("branding");
        assertThat(config).containsKey("translations");
        assertThat(config.get("language")).isEqualTo("EN");
        @SuppressWarnings("unchecked")
        Map<String, Object> brandingMap = (Map<String, Object>) config.get("branding");
        assertThat(brandingMap.get("primaryColor")).isEqualTo("#123456");
        assertThat(brandingMap.get("portalTitle")).isEqualTo("Custom Portal");
    }

    @Test
    @DisplayName("getPortalConfig → sans branding → défauts")
    void getPortalConfig_noBranding() {
        UUID companyId = UUID.randomUUID();
        when(brandingRepo.findByCompanyId(companyId)).thenReturn(Optional.empty());

        Map<String, Object> config = service.getPortalConfig(companyId, "FR");

        @SuppressWarnings("unchecked")
        Map<String, Object> brandingMap = (Map<String, Object>) config.get("branding");
        assertThat(brandingMap.get("primaryColor")).isEqualTo("#2563EB");
        assertThat(brandingMap.get("portalTitle")).isEqualTo("Client Portal");
    }

    @Test
    @DisplayName("getSupportedLanguages → retourne les 5 langues")
    void getSupportedLanguages() {
        assertThat(service.getSupportedLanguages()).containsExactly("FR", "EN", "ES", "DE", "AR");
    }
}
