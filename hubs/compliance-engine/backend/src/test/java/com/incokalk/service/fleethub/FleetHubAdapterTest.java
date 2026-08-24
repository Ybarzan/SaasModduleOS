package com.incokalk.service.fleethub;

import com.incokalk.model.FleetHubConfig;
import com.incokalk.repository.FleetHubConfigRepository;
import com.incokalk.service.tracking.LivePosition;
import com.incokalk.service.tracking.TrackingUpdate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("FleetHubAdapter — Tests unitaires")
class FleetHubAdapterTest {

    @Mock FleetHubConfigRepository configRepo;
    @Mock FleetHubClient client;

    private FleetHubAdapter adapter;
    private UUID companyId;
    private FleetHubConfig config;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        adapter = new FleetHubAdapter(configRepo, client);
        companyId = UUID.randomUUID();
        config = FleetHubConfig.builder().id(UUID.randomUUID()).name("Flotte principale").isActive(true).build();
    }

    private FleetHubVehicle vehicle(String registration) {
        return FleetHubVehicle.builder()
                .truckId(1L).registration(registration).brand("Volvo").model("FH16")
                .driverName("Jean Dupont").latitude(48.85).longitude(2.35).speedKph(72.0)
                .status("ROULAGE").lastGpsUpdate(LocalDateTime.of(2026, 8, 24, 10, 0))
                .build();
    }

    @Test
    @DisplayName("getProviderType/getName : identifie le provider comme FLEET_HUB")
    void identity() {
        assertThat(adapter.getProviderType()).isEqualTo("FLEET_HUB");
        assertThat(adapter.getName()).isEqualTo("Fleet Hub");
    }

    @Test
    @DisplayName("isAvailable : aucune config active -> false")
    void isAvailable_noConfig_false() {
        when(configRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of());
        assertThat(adapter.isAvailable(companyId)).isFalse();
    }

    @Test
    @DisplayName("isAvailable : une config active -> true")
    void isAvailable_withConfig_true() {
        when(configRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of(config));
        assertThat(adapter.isAvailable(companyId)).isTrue();
    }

    @Test
    @DisplayName("getCurrentPosition : véhicule trouvé -> position mappée correctement")
    void getCurrentPosition_found_mapsPosition() {
        when(configRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of(config));
        when(client.getVehicles(config)).thenReturn(List.of(vehicle("AB-123-CD")));

        LivePosition pos = adapter.getCurrentPosition("AB-123-CD", companyId);

        assertThat(pos).isNotNull();
        assertThat(pos.getLatitude()).isEqualTo(48.85);
        assertThat(pos.getLongitude()).isEqualTo(2.35);
        assertThat(pos.getSpeed()).isEqualTo(72.0);
        assertThat(pos.getSource()).isEqualTo("Fleet Hub");
        assertThat(pos.getVesselName()).isEqualTo("AB-123-CD");
    }

    @Test
    @DisplayName("getCurrentPosition : correspondance insensible à la casse et aux espaces")
    void getCurrentPosition_matchesRegistrationCaseInsensitive() {
        when(configRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of(config));
        when(client.getVehicles(config)).thenReturn(List.of(vehicle("ab-123-cd")));

        LivePosition pos = adapter.getCurrentPosition(" AB-123-CD ", companyId);

        assertThat(pos).isNotNull();
    }

    @Test
    @DisplayName("getCurrentPosition : véhicule non trouvé -> null")
    void getCurrentPosition_notFound_null() {
        when(configRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of(config));
        when(client.getVehicles(config)).thenReturn(List.of(vehicle("XY-999-ZZ")));

        assertThat(adapter.getCurrentPosition("AB-123-CD", companyId)).isNull();
    }

    @Test
    @DisplayName("getCurrentPosition : aucune config active -> null, aucun appel au client")
    void getCurrentPosition_noConfig_nullWithoutClientCall() {
        when(configRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of());

        assertThat(adapter.getCurrentPosition("AB-123-CD", companyId)).isNull();
        org.mockito.Mockito.verifyNoInteractions(client);
    }

    @Test
    @DisplayName("getCurrentPosition : immatriculation vide -> null, aucun appel au client")
    void getCurrentPosition_blankRegistration_nullWithoutClientCall() {
        assertThat(adapter.getCurrentPosition("", companyId)).isNull();
        assertThat(adapter.getCurrentPosition(null, companyId)).isNull();
        org.mockito.Mockito.verifyNoInteractions(configRepo, client);
    }

    @Test
    @DisplayName("getCurrentPosition : échec du client sur une config -> continue avec les configs suivantes")
    void getCurrentPosition_oneConfigFails_triesNext() {
        FleetHubConfig secondConfig = FleetHubConfig.builder().id(UUID.randomUUID()).name("Flotte secondaire").isActive(true).build();
        when(configRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of(config, secondConfig));
        when(client.getVehicles(config)).thenThrow(new FleetHubClient.FleetHubException("Connexion refusée"));
        when(client.getVehicles(secondConfig)).thenReturn(List.of(vehicle("AB-123-CD")));

        LivePosition pos = adapter.getCurrentPosition("AB-123-CD", companyId);

        assertThat(pos).isNotNull();
    }

    @Test
    @DisplayName("getTrackingInfo : véhicule trouvé -> une seule mise à jour (instantané, pas d'historique)")
    void getTrackingInfo_found_returnsSingleUpdate() {
        when(configRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of(config));
        when(client.getVehicles(config)).thenReturn(List.of(vehicle("AB-123-CD")));

        List<TrackingUpdate> updates = adapter.getTrackingInfo("AB-123-CD", companyId);

        assertThat(updates).hasSize(1);
        assertThat(updates.get(0).getStatus()).isEqualTo("ROULAGE");
        assertThat(updates.get(0).getDescription()).contains("Jean Dupont");
    }

    @Test
    @DisplayName("getTrackingInfo : véhicule non trouvé -> liste vide")
    void getTrackingInfo_notFound_empty() {
        when(configRepo.findByCompanyIdAndIsActiveTrue(companyId)).thenReturn(List.of());

        assertThat(adapter.getTrackingInfo("AB-123-CD", companyId)).isEmpty();
    }
}
