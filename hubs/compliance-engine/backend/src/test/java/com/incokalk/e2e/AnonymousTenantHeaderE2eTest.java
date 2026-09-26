package com.incokalk.e2e;

import com.incokalk.model.FleetHubConfig;
import com.incokalk.repository.FleetHubConfigRepository;
import com.incokalk.service.fleethub.FleetHubClient;
import com.incokalk.service.fleethub.FleetHubVehicle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.*;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * TenantFilter ne doit fixer un tenant que pour un appelant authentifié. Avant correctif,
 * un anonyme envoyant X-Tenant-ID (ou un Host "slug.domaine") obtenait TenantContext =
 * société visée sur les endpoints permitAll. Cas concret : POST /v1/tracking/lookup en
 * mode FLEET_HUB renvoyait la position GPS et le nom du chauffeur d'un camion de la
 * victime, via les identifiants fleet-hub de celle-ci.
 */
class AnonymousTenantHeaderE2eTest extends E2eTestBase {

    private static final String REGISTRATION = "AB-123-CD";

    @MockBean
    private FleetHubClient fleetHubClient;

    @Autowired
    private FleetHubConfigRepository fleetHubConfigRepo;

    private UUID victimCompanyId;
    private String victimSlug;
    private String victimToken;

    @BeforeEach
    void setUpVictimFleet() {
        AuthResult victim = registerAndSetToken();
        victimToken = victim.token();
        var company = companyRepo.findById(
                userRepo.findById(victim.userId()).orElseThrow().getCompany().getId()).orElseThrow();
        victimCompanyId = company.getId();
        victimSlug = company.getSlug();

        fleetHubConfigRepo.save(FleetHubConfig.builder()
                .company(company).name("Flotte victime").baseUrl("http://fleet.invalid")
                .username("u").password("p").isActive(true).build());

        when(fleetHubClient.getVehicles(any())).thenReturn(List.of(FleetHubVehicle.builder()
                .registration(REGISTRATION).driverName("Jean Victime")
                .latitude(48.85).longitude(2.35).status("EN_ROUTE")
                .lastGpsUpdate(LocalDateTime.now()).build()));
        clearInvocations(fleetHubClient);
    }

    private ResponseEntity<List> lookup(HttpHeaders headers) {
        headers.setContentType(MediaType.APPLICATION_JSON);
        var body = Map.of("trackingNumber", REGISTRATION, "mode", "FLEET_HUB");
        return restTemplate.exchange(baseUrl + "/v1/tracking/lookup", HttpMethod.POST,
                new HttpEntity<>(body, headers), List.class);
    }

    @Test
    @DisplayName("Anonyme + X-Tenant-ID d'une autre société : aucune donnée de flotte")
    void anonymousWithTenantHeader_getsNothing() {
        var h = new HttpHeaders();
        h.set("X-Tenant-ID", victimCompanyId.toString());

        var resp = lookup(h);

        assertThat(resp.getStatusCode().is2xxSuccessful()).as("statut %s", resp.getStatusCode()).isTrue();
        assertThat(resp.getBody()).isEmpty();
        verify(fleetHubClient, never()).getVehicles(any());
    }

    @Test
    @DisplayName("Anonyme + Host slug.domaine d'une autre société : aucune donnée de flotte")
    void anonymousWithHostSlug_getsNothing() throws Exception {
        // HttpURLConnection interdit de réécrire l'en-tête Host : requête HTTP brute.
        String json = "{\"trackingNumber\":\"" + REGISTRATION + "\",\"mode\":\"FLEET_HUB\"}";
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        String response;
        try (Socket s = new Socket("localhost", port)) {
            OutputStream out = s.getOutputStream();
            out.write(("POST /api/v1/tracking/lookup HTTP/1.1\r\n"
                    + "Host: " + victimSlug + ".praxio.test\r\n"
                    + "Content-Type: application/json\r\n"
                    + "Content-Length: " + payload.length + "\r\n"
                    + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
            out.write(payload);
            out.flush();
            InputStream in = s.getInputStream();
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            in.transferTo(buf);
            response = buf.toString(StandardCharsets.UTF_8);
        }

        assertThat(response).startsWith("HTTP/1.1 200");
        assertThat(response).doesNotContain("Jean Victime").doesNotContain("48.85");
        verify(fleetHubClient, never()).getVehicles(any());
    }

    @Test
    @DisplayName("Membre authentifié (JWT) + X-Tenant-ID de sa société : données visibles")
    void memberWithJwt_stillResolvesTenant() {
        var h = new HttpHeaders();
        h.setBearerAuth(victimToken);
        h.set("X-Tenant-ID", victimCompanyId.toString());

        var resp = lookup(h);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).hasSize(1);
        assertThat(resp.getBody().get(0).toString()).contains("Jean Victime");
    }

    @Test
    @DisplayName("Utilisateur authentifié non membre + X-Tenant-ID de la victime : rien")
    void nonMemberWithJwt_getsNothing() {
        registerAndSetToken(); // attaquant, dans sa propre société sans flotte
        var h = new HttpHeaders();
        h.setBearerAuth(jwtToken);
        h.set("X-Tenant-ID", victimCompanyId.toString());

        var resp = lookup(h);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isEmpty();
        verify(fleetHubClient, never()).getVehicles(any());
    }

    @Test
    @DisplayName("Clé API (ic_) de la société + X-Tenant-ID correspondant : chemin API préservé")
    void apiKeyWithTenantHeader_stillWorks() {
        var created = post("/v1/api-keys", Map.of("name", "intégration", "plan", "ENTERPRISE"));
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String rawKey = jsonPath(created, "key");
        assertThat(rawKey).startsWith("ic_");

        var h = new HttpHeaders();
        h.set("X-API-Key", rawKey);
        h.set("X-Tenant-ID", victimCompanyId.toString());

        var resp = lookup(h);

        assertThat(resp.getStatusCode().is2xxSuccessful()).as("statut %s", resp.getStatusCode()).isTrue();
        assertThat(resp.getBody()).hasSize(1);
        assertThat(resp.getBody().get(0).toString()).contains("Jean Victime");
    }
}
