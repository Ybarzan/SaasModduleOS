package com.incokalk.controller;

import com.incokalk.model.Company;
import com.incokalk.model.CompanyBranding;
import com.incokalk.repository.CompanyBrandingRepository;
import com.incokalk.service.BrandingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BrandingControllerTest extends ControllerTestBase {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompanyBrandingRepository brandingRepo;

    @MockBean
    private BrandingService brandingService;

    @Test
    @DisplayName("GET /v1/branding → retourne branding existant")
    void getBranding_found() throws Exception {
        CompanyBranding branding = CompanyBranding.builder()
                .company(Company.builder().id(companyId).build())
                .primaryColor("#FF0000")
                .portalTitle("My Portal")
                .build();

        when(brandingService.getBranding(companyId)).thenReturn(branding);

        mockMvc.perform(get("/v1/branding")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryColor").value("#FF0000"))
                .andExpect(jsonPath("$.portalTitle").value("My Portal"));
    }

    @Test
    @DisplayName("GET /v1/branding → pas de branding → objet vide")
    void getBranding_notFound() throws Exception {
        when(brandingService.getBranding(companyId)).thenReturn(null);

        mockMvc.perform(get("/v1/branding")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isMap());
    }

    @Test
    @DisplayName("PUT /v1/branding → met à jour le branding")
    void updateBranding() throws Exception {
        CompanyBranding updated = CompanyBranding.builder()
                .primaryColor("#00FF00")
                .portalTitle("Updated Portal")
                .build();

        when(brandingService.updateBranding(eq(companyId), any(Map.class))).thenReturn(updated);

        mockMvc.perform(put("/v1/branding")
                        .header("Authorization", authHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"primaryColor\":\"#00FF00\",\"portalTitle\":\"Updated Portal\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryColor").value("#00FF00"))
                .andExpect(jsonPath("$.portalTitle").value("Updated Portal"));
    }

    @Test
    @DisplayName("GET /v1/branding/portal-config → config publique")
    void getPortalConfig() throws Exception {
        UUID publicCompanyId = UUID.randomUUID();
        CompanyBranding branding = CompanyBranding.builder()
                .company(Company.builder().id(publicCompanyId).build())
                .primaryColor("#123456")
                .portalTitle("Public Portal")
                .build();

        when(brandingService.getPortalConfig(publicCompanyId, "EN")).thenReturn(Map.of(
                "language", "EN",
                "branding", Map.of(
                        "primaryColor", "#123456",
                        "portalTitle", "Public Portal"
                ),
                "translations", Map.of("nav.home", "Home")
        ));

        mockMvc.perform(get("/v1/branding/portal-config")
                        .header("Authorization", authHeader())
                        .param("companyId", publicCompanyId.toString())
                        .param("lang", "EN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.language").value("EN"))
                .andExpect(jsonPath("$.branding.primaryColor").value("#123456"))
                .andExpect(jsonPath("$.branding.portalTitle").value("Public Portal"));
    }

    @Test
    @DisplayName("GET /v1/branding/translations → retourne traductions FR")
    void getTranslations() throws Exception {
        when(brandingService.getTranslations("FR")).thenReturn(Map.of(
                "nav.home", "Accueil",
                "nav.shipments", "Envois"
        ));

        mockMvc.perform(get("/v1/branding/translations")
                        .header("Authorization", authHeader())
                        .param("lang", "FR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['nav.home']").value("Accueil"))
                .andExpect(jsonPath("$['nav.shipments']").value("Envois"));
    }

    @Test
    @DisplayName("GET /v1/branding/languages → liste des langues supportées")
    void getLanguages() throws Exception {
        when(brandingService.getSupportedLanguages()).thenReturn(
                java.util.List.of("FR", "EN", "ES", "DE", "AR")
        );

        mockMvc.perform(get("/v1/branding/languages")
                        .header("Authorization", authHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("FR"))
                .andExpect(jsonPath("$[4]").value("AR"));
    }
}