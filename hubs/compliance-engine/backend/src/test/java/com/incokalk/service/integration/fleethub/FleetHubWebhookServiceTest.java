package com.incokalk.service.integration.fleethub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.dto.integration.fleethub.FleetHubShipmentEventRequest;
import com.incokalk.dto.integration.fleethub.FleetHubWebhookSettingsDto;
import com.incokalk.model.Company;
import com.incokalk.model.EventOutbox;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.EventOutboxRepository;
import com.incokalk.security.ApiKeyHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("FleetHubWebhookService — Tests unitaires")
class FleetHubWebhookServiceTest {

    CompanyRepository companyRepo;
    EventOutboxRepository eventOutboxRepo;
    FleetHubWebhookService service;
    UUID companyId;

    @BeforeEach
    void setUp() {
        companyRepo = mock(CompanyRepository.class);
        eventOutboxRepo = mock(EventOutboxRepository.class);
        service = new FleetHubWebhookService(companyRepo, eventOutboxRepo, new ObjectMapper());
        companyId = UUID.randomUUID();
    }

    @Test
    @DisplayName("settings → désactivé par défaut, jamais de clé renvoyée")
    void settings_defaultsToDisabled() {
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(Company.builder().id(companyId).build()));

        FleetHubWebhookSettingsDto dto = service.settings(companyId);

        assertThat(dto.fleethubWebhookEnabled()).isFalse();
        assertThat(dto.fleethubWebhookKey()).isNull();
    }

    @Test
    @DisplayName("enable → génère une clé si absente, l'expose une fois en clair, stocke son hash")
    void enable_generatesKey_whenAbsent() {
        Company company = Company.builder().id(companyId).build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

        FleetHubWebhookSettingsDto dto = service.enable(companyId);

        assertThat(dto.fleethubWebhookEnabled()).isTrue();
        assertThat(dto.fleethubWebhookKey()).isNotBlank();
        assertThat(company.getFleethubWebhookKeyHash()).isEqualTo(ApiKeyHasher.hash(dto.fleethubWebhookKey()));
        verify(companyRepo).save(company);
    }

    @Test
    @DisplayName("enable → réutilise le hash existant sans le régénérer, et ne renvoie plus la clé")
    void enable_reusesExistingKey() {
        String existingHash = ApiKeyHasher.hash("existing-key");
        Company company = Company.builder().id(companyId).fleethubWebhookKeyHash(existingHash).build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

        FleetHubWebhookSettingsDto dto = service.enable(companyId);

        assertThat(dto.fleethubWebhookKey()).isNull();
        assertThat(company.getFleethubWebhookKeyHash()).isEqualTo(existingHash);
    }

    @Test
    @DisplayName("disable → désactive et efface le hash (une réactivation en génère un nouveau)")
    void disable_clearsStoredKeyHash() {
        Company company = Company.builder().id(companyId).fleethubWebhookEnabled(true).fleethubWebhookKeyHash("k").build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

        service.disable(companyId);

        assertThat(company.isFleethubWebhookEnabled()).isFalse();
        assertThat(company.getFleethubWebhookKeyHash()).isNull();
    }

    @Test
    @DisplayName("resolveCompanyId → clé absente -> 401")
    void resolveCompanyId_missingKey_unauthorized() {
        assertThatThrownBy(() -> service.resolveCompanyId(null))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("resolveCompanyId → clé invalide ou désactivée -> 401")
    void resolveCompanyId_invalidKey_unauthorized() {
        when(companyRepo.findByFleethubWebhookKeyHashAndFleethubWebhookEnabledTrue(ApiKeyHasher.hash("bad-key")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.resolveCompanyId("bad-key"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode().value())
                .isEqualTo(401);
    }

    @Test
    @DisplayName("resolveCompanyId → clé valide -> id de la société propriétaire")
    void resolveCompanyId_validKey_returnsCompanyId() {
        Company company = Company.builder().id(companyId).fleethubWebhookEnabled(true)
                .fleethubWebhookKeyHash(ApiKeyHasher.hash("good-key")).build();
        when(companyRepo.findByFleethubWebhookKeyHashAndFleethubWebhookEnabledTrue(ApiKeyHasher.hash("good-key")))
                .thenReturn(Optional.of(company));

        assertThat(service.resolveCompanyId("good-key")).isEqualTo(companyId);
    }

    @Test
    @DisplayName("recordEvent → écrit une ligne event_outbox préfixée FLEETHUB_, PENDING, avec le payload sérialisé")
    void recordEvent_writesOutboxRow() {
        FleetHubShipmentEventRequest request = new FleetHubShipmentEventRequest(
                "SHIPMENT_STATUS_CHANGE", UUID.randomUUID(), "FH-1", "EN_TRANSIT", "DELIVERED", "LIVE");

        service.recordEvent(companyId, request);

        ArgumentCaptor<EventOutbox> captor = ArgumentCaptor.forClass(EventOutbox.class);
        verify(eventOutboxRepo).save(captor.capture());
        EventOutbox saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("FLEETHUB_SHIPMENT_STATUS_CHANGE");
        assertThat(saved.getCompanyId()).isEqualTo(companyId);
        assertThat(saved.getStatus()).isEqualTo(EventOutbox.Status.PENDING);
        assertThat(saved.getPayload()).contains("FH-1").contains("DELIVERED");
    }
}
