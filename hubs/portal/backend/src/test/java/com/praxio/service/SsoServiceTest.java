package com.praxio.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.praxio.config.HubUrlsProperties;
import com.praxio.dto.SsoLaunchResultDto;
import com.praxio.dto.sso.ComplianceEngineLoginResponse;
import com.praxio.dto.sso.EmailLoginRequest;
import com.praxio.dto.sso.FleetHubLoginResponse;
import com.praxio.dto.sso.UsernameLoginRequest;
import com.praxio.model.SsoCredentials;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("SsoService — Tests unitaires")
class SsoServiceTest {

    RestTemplate restTemplate;
    SsoCredentialsService ssoCredentialsService;
    HubUrlsProperties hubUrls;
    SsoService service;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        ssoCredentialsService = mock(SsoCredentialsService.class);
        hubUrls = new HubUrlsProperties(
                "http://ce-backend.test", "http://ce-frontend.test",
                "http://fh-backend.test", "http://fh-frontend.test",
                "http://lx-backend.test", "http://lx-frontend.test");
        service = new SsoService(restTemplate, ssoCredentialsService, hubUrls, new ObjectMapper());
    }

    @Test
    @DisplayName("launch → hub inconnu -> 400")
    void launch_unknownHub_badRequest() {
        assertThatThrownBy(() -> service.launch("nope"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("launch(complianceEngine) → identifiants absents -> 400, aucun appel réseau")
    void launch_complianceEngine_noCredentials_badRequest() {
        when(ssoCredentialsService.getCredentialsOrEmpty())
                .thenReturn(SsoCredentials.builder().id(1L).build());

        assertThatThrownBy(() -> service.launch("complianceEngine"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("launch(complianceEngine) → envoie {email,password} et renvoie une redirection avec le payload encodé")
    void launch_complianceEngine_success_buildsRedirectUrl() throws Exception {
        when(ssoCredentialsService.getCredentialsOrEmpty()).thenReturn(SsoCredentials.builder()
                .id(1L).complianceEngineEmail("ops@acme.fr").complianceEnginePassword("secret").build());
        ComplianceEngineLoginResponse loginResponse = new ComplianceEngineLoginResponse(
                "jwt-token", "refresh-token", "user-1", "ops@acme.fr", "PRO", "OWNER", "Ops Acme");
        when(restTemplate.postForObject(
                eq("http://ce-backend.test/api/v1/auth/login"),
                eq(new EmailLoginRequest("ops@acme.fr", "secret")),
                eq(ComplianceEngineLoginResponse.class)))
                .thenReturn(loginResponse);

        SsoLaunchResultDto result = service.launch("complianceEngine");

        assertThat(result.redirectUrl()).startsWith("http://ce-frontend.test/sso-entry#");
        String payload = result.redirectUrl().substring(result.redirectUrl().indexOf('#') + 1);
        String json = new String(Base64.getUrlDecoder().decode(payload), StandardCharsets.UTF_8);
        assertThat(json).contains("\"token\":\"jwt-token\"").contains("\"email\":\"ops@acme.fr\"");
    }

    @Test
    @DisplayName("launch(fleetHub) → envoie {username,password} (pas email)")
    void launch_fleetHub_usesUsernameField() {
        when(ssoCredentialsService.getCredentialsOrEmpty()).thenReturn(SsoCredentials.builder()
                .id(1L).fleetHubEmail("ops@acme.fr").fleetHubPassword("secret").build());
        when(restTemplate.postForObject(
                eq("http://fh-backend.test/api/auth/login"),
                eq(new UsernameLoginRequest("ops@acme.fr", "secret")),
                eq(FleetHubLoginResponse.class)))
                .thenReturn(new FleetHubLoginResponse("jwt", "ops@acme.fr", "Ops", "OWNER", "ops@acme.fr",
                        42L, "Acme", "ABC123", "PRO", "ACTIVE", true, false, false));

        SsoLaunchResultDto result = service.launch("fleetHub");

        assertThat(result.redirectUrl()).startsWith("http://fh-frontend.test/sso-entry#");
    }

    @Test
    @DisplayName("launch(fleetHub) → 2FA requis -> 409, pas de redirection")
    void launch_fleetHub_totpRequired_conflict() {
        when(ssoCredentialsService.getCredentialsOrEmpty()).thenReturn(SsoCredentials.builder()
                .id(1L).fleetHubEmail("ops@acme.fr").fleetHubPassword("secret").build());
        when(restTemplate.postForObject(any(String.class), any(), eq(FleetHubLoginResponse.class)))
                .thenReturn(new FleetHubLoginResponse(null, "ops@acme.fr", "Ops", "OWNER", "ops@acme.fr",
                        42L, "Acme", "ABC123", "PRO", "ACTIVE", true, true, true));

        assertThatThrownBy(() -> service.launch("fleetHub"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("launch → identifiants stockés rejetés par le hub (401) -> 401 avec message clair")
    void launch_hubRejectsCredentials_unauthorized() {
        when(ssoCredentialsService.getCredentialsOrEmpty()).thenReturn(SsoCredentials.builder()
                .id(1L).logistixEmail("ops@acme.fr").logistixPassword("wrong").build());
        when(restTemplate.postForObject(any(String.class), any(), any(Class.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.UNAUTHORIZED, "Unauthorized", HttpHeaders.EMPTY, null, null));

        assertThatThrownBy(() -> service.launch("logistix"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @DisplayName("launch → hub injoignable -> 503")
    void launch_hubUnreachable_serviceUnavailable() {
        when(ssoCredentialsService.getCredentialsOrEmpty()).thenReturn(SsoCredentials.builder()
                .id(1L).logistixEmail("ops@acme.fr").logistixPassword("secret").build());
        when(restTemplate.postForObject(any(String.class), any(), any(Class.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        assertThatThrownBy(() -> service.launch("logistix"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }
}
