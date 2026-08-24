package com.incokalk.service.fleethub;

import com.incokalk.model.FleetHubConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@DisplayName("FleetHubClient — Tests unitaires")
class FleetHubClientTest {

    @Mock RestTemplate restTemplate;

    private FleetHubClient client;
    private FleetHubConfig config;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        client = new FleetHubClient(restTemplate);
        config = FleetHubConfig.builder()
                .baseUrl("https://fleethub.example.com")
                .username("integration@acme.io")
                .password("secret")
                .build();
    }

    @Test
    @DisplayName("login : réponse valide -> renvoie le token")
    void login_success_returnsToken() {
        when(restTemplate.exchange(eq("https://fleethub.example.com/api/auth/login"), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("token", "jwt-abc", "totpRequired", false)));

        assertThat(client.login(config)).isEqualTo("jwt-abc");
    }

    @Test
    @DisplayName("login : trailing slash sur baseUrl -> pas de double slash dans l'URL")
    void login_trimsTrailingSlash() {
        config.setBaseUrl("https://fleethub.example.com/");
        when(restTemplate.exchange(eq("https://fleethub.example.com/api/auth/login"), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("token", "jwt-abc", "totpRequired", false)));

        assertThat(client.login(config)).isEqualTo("jwt-abc");
    }

    @Test
    @DisplayName("login : totpRequired=true -> échoue avec un message explicite")
    void login_totpRequired_throwsExplicitError() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("totpRequired", true)));

        assertThatThrownBy(() -> client.login(config))
                .isInstanceOf(FleetHubClient.FleetHubException.class)
                .hasMessageContaining("double authentification");
    }

    @Test
    @DisplayName("login : réponse sans token -> échoue")
    void login_noToken_throws() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("totpRequired", false)));

        assertThatThrownBy(() -> client.login(config))
                .isInstanceOf(FleetHubClient.FleetHubException.class)
                .hasMessageContaining("sans token");
    }

    @Test
    @DisplayName("login : statut non-2xx -> échoue")
    void login_non2xxStatus_throws() {
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());

        assertThatThrownBy(() -> client.login(config))
                .isInstanceOf(FleetHubClient.FleetHubException.class);
    }

    @Test
    @DisplayName("getVehicles : login puis appel authentifié -> renvoie la liste désérialisée")
    void getVehicles_success_returnsVehicles() {
        when(restTemplate.exchange(eq("https://fleethub.example.com/api/auth/login"), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("token", "jwt-abc", "totpRequired", false)));

        FleetHubVehicle vehicle = FleetHubVehicle.builder()
                .truckId(1L).registration("AB-123-CD").brand("Volvo").model("FH16")
                .driverName("Jean Dupont").latitude(48.85).longitude(2.35).speedKph(72.0)
                .status("EN_ROUTE").lastGpsUpdate(LocalDateTime.now())
                .build();
        when(restTemplate.exchange(eq("https://fleethub.example.com/api/map/vehicles"), eq(HttpMethod.GET), any(), eq(FleetHubVehicle[].class)))
                .thenReturn(ResponseEntity.ok(new FleetHubVehicle[]{vehicle}));

        List<FleetHubVehicle> result = client.getVehicles(config);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getRegistration()).isEqualTo("AB-123-CD");
    }

    @Test
    @DisplayName("getVehicles : authentifie avec le token Bearer obtenu au login")
    void getVehicles_usesBearerTokenFromLogin() {
        when(restTemplate.exchange(eq("https://fleethub.example.com/api/auth/login"), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("token", "jwt-xyz", "totpRequired", false)));
        when(restTemplate.exchange(eq("https://fleethub.example.com/api/map/vehicles"), eq(HttpMethod.GET), any(), eq(FleetHubVehicle[].class)))
                .thenReturn(ResponseEntity.ok(new FleetHubVehicle[]{}));

        client.getVehicles(config);

        org.mockito.ArgumentCaptor<HttpEntity> captor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
        org.mockito.Mockito.verify(restTemplate).exchange(
                eq("https://fleethub.example.com/api/map/vehicles"), eq(HttpMethod.GET), captor.capture(), eq(FleetHubVehicle[].class));
        assertThat(captor.getValue().getHeaders().getFirst("Authorization")).isEqualTo("Bearer jwt-xyz");
    }

    @Test
    @DisplayName("getVehicles : statut non-2xx -> échoue")
    void getVehicles_non2xxStatus_throws() {
        when(restTemplate.exchange(eq("https://fleethub.example.com/api/auth/login"), eq(HttpMethod.POST), any(), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(Map.of("token", "jwt-abc", "totpRequired", false)));
        when(restTemplate.exchange(eq("https://fleethub.example.com/api/map/vehicles"), eq(HttpMethod.GET), any(), eq(FleetHubVehicle[].class)))
                .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());

        assertThatThrownBy(() -> client.getVehicles(config))
                .isInstanceOf(FleetHubClient.FleetHubException.class);
    }
}
