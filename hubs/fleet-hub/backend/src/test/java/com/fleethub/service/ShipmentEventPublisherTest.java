package com.fleethub.service;

import com.fleethub.config.ApiKeyCrypto;
import com.fleethub.model.Company;
import com.fleethub.model.TrackingEvent;
import com.fleethub.repository.CompanyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("ShipmentEventPublisher — Tests unitaires")
class ShipmentEventPublisherTest {

    private static final String BASE_URL = "http://compliance-engine.test";
    private static final String WEBHOOK_URL = BASE_URL + "/api/v1/webhooks/fleethub/shipment-events";

    NotificationService notificationService;
    CompanyRepository companyRepository;
    RestTemplate restTemplate;
    ApiKeyCrypto apiKeyCrypto;
    ShipmentEventPublisher publisher;

    @BeforeEach
    void setUp() {
        notificationService = mock(NotificationService.class);
        companyRepository = mock(CompanyRepository.class);
        restTemplate = mock(RestTemplate.class);
        apiKeyCrypto = new ApiKeyCrypto("test-secret-not-for-production-use");
        publisher = new ShipmentEventPublisher(notificationService, companyRepository, restTemplate, apiKeyCrypto);
        setComplianceEngineBaseUrl(publisher, BASE_URL);
    }

    private static void setComplianceEngineBaseUrl(ShipmentEventPublisher publisher, String baseUrl) {
        try {
            var field = ShipmentEventPublisher.class.getDeclaredField("complianceEngineBaseUrl");
            field.setAccessible(true);
            field.set(publisher, baseUrl);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    private Company companyWithWebhookKey(String key) {
        Company company = new Company();
        company.setId(42L);
        company.setComplianceEngineWebhookKey(key);
        return company;
    }

    @Test
    @DisplayName("shipmentStatusChanged → pousse une notification de changement de statut")
    void shipmentStatusChanged_notifiesStatusChange() {
        when(companyRepository.findById(42L)).thenReturn(Optional.of(companyWithWebhookKey(null)));
        UUID shipmentId = UUID.randomUUID();

        publisher.shipmentStatusChanged(shipmentId, "ORD-001", "EN_TRANSIT", "LIVRE",
                42L, 7L, TrackingEvent.DataSource.MANUAL);

        verify(notificationService).notifyShipmentStatusChanged(42L, "ORD-001", "EN_TRANSIT", "LIVRE");
    }

    @Test
    @DisplayName("shipmentCreated → ne déclenche aucune notification (création de routine)")
    void shipmentCreated_doesNotNotify() {
        when(companyRepository.findById(42L)).thenReturn(Optional.of(companyWithWebhookKey(null)));

        publisher.shipmentCreated(UUID.randomUUID(), "ORD-002", 42L);

        verifyNoInteractions(notificationService);
    }

    @Test
    @DisplayName("Pas de clé webhook compliance-engine configurée → aucun appel HTTP")
    void noWebhookKey_doesNotCallComplianceEngine() {
        when(companyRepository.findById(42L)).thenReturn(Optional.of(companyWithWebhookKey(null)));

        publisher.shipmentCreated(UUID.randomUUID(), "ORD-003", 42L);

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("Clé webhook vide (blank) configurée → aucun appel HTTP")
    void blankWebhookKey_doesNotCallComplianceEngine() {
        when(companyRepository.findById(42L)).thenReturn(Optional.of(companyWithWebhookKey("   ")));

        publisher.shipmentCreated(UUID.randomUUID(), "ORD-003b", 42L);

        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("shipmentCreated avec clé configurée → POST vers compliance-engine avec le bon header et le bon corps")
    void shipmentCreated_withWebhookKey_postsToComplianceEngine() {
        when(companyRepository.findById(42L)).thenReturn(Optional.of(companyWithWebhookKey(apiKeyCrypto.encrypt("secret-webhook-key"))));
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.accepted().build());
        UUID shipmentId = UUID.randomUUID();

        publisher.shipmentCreated(shipmentId, "ORD-004", 42L);

        ArgumentCapturingHelper.captureAndAssert(restTemplate, WEBHOOK_URL, entity -> {
            assertThat(entity.getHeaders().getFirst("X-FleetHub-Webhook-Key")).isEqualTo("secret-webhook-key");
            assertThat(entity.getHeaders().getContentType()).isEqualTo(org.springframework.http.MediaType.APPLICATION_JSON);
            Object body = entity.getBody();
            assertThat(body).isNotNull();
            assertThat(body.toString()).contains("SHIPMENT_CREATED").contains(shipmentId.toString()).contains("ORD-004");
        });
    }

    @Test
    @DisplayName("shipmentStatusChanged avec clé configurée → POST vers compliance-engine avec eventType/dataSource corrects, et notification locale toujours envoyée")
    void shipmentStatusChanged_withWebhookKey_postsToComplianceEngine() {
        when(companyRepository.findById(42L)).thenReturn(Optional.of(companyWithWebhookKey(apiKeyCrypto.encrypt("secret-webhook-key"))));
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.accepted().build());
        UUID shipmentId = UUID.randomUUID();

        publisher.shipmentStatusChanged(shipmentId, "ORD-005", "EN_TRANSIT", "LIVRE",
                42L, 7L, TrackingEvent.DataSource.LIVE);

        verify(notificationService).notifyShipmentStatusChanged(42L, "ORD-005", "EN_TRANSIT", "LIVRE");
        ArgumentCapturingHelper.captureAndAssert(restTemplate, WEBHOOK_URL, entity -> {
            assertThat(entity.getHeaders().getFirst("X-FleetHub-Webhook-Key")).isEqualTo("secret-webhook-key");
            Object body = entity.getBody();
            assertThat(body).isNotNull();
            assertThat(body.toString())
                    .contains("SHIPMENT_STATUS_CHANGE")
                    .contains("EN_TRANSIT")
                    .contains("LIVRE")
                    .contains("LIVE");
        });
    }

    @Test
    @DisplayName("Échec HTTP (exception réseau) → ne remonte jamais, notification locale toujours envoyée")
    void httpFailure_doesNotPropagate_andLocalNotificationStillHappens() {
        when(companyRepository.findById(42L)).thenReturn(Optional.of(companyWithWebhookKey(apiKeyCrypto.encrypt("secret-webhook-key"))));
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenThrow(new RuntimeException("connection refused"));

        publisher.shipmentStatusChanged(UUID.randomUUID(), "ORD-006", "EN_TRANSIT", "LIVRE",
                42L, 7L, TrackingEvent.DataSource.MANUAL);

        verify(notificationService).notifyShipmentStatusChanged(42L, "ORD-006", "EN_TRANSIT", "LIVRE");
    }

    @Test
    @DisplayName("Échec HTTP sur shipmentCreated → ne remonte jamais")
    void httpFailure_onShipmentCreated_doesNotPropagate() {
        when(companyRepository.findById(42L)).thenReturn(Optional.of(companyWithWebhookKey(apiKeyCrypto.encrypt("secret-webhook-key"))));
        when(restTemplate.postForEntity(anyString(), any(), eq(Void.class)))
                .thenThrow(new RuntimeException("timeout"));

        publisher.shipmentCreated(UUID.randomUUID(), "ORD-007", 42L);
        // Ne doit pas lancer d'exception -- si on arrive ici, c'est réussi.
    }

    @Test
    @DisplayName("Société introuvable → aucun appel HTTP, aucune exception")
    void companyNotFound_doesNotCallComplianceEngine() {
        when(companyRepository.findById(99L)).thenReturn(Optional.empty());

        publisher.shipmentCreated(UUID.randomUUID(), "ORD-008", 99L);

        verifyNoInteractions(restTemplate);
    }

    /** Petite aide pour capturer le HttpEntity passé à restTemplate.postForEntity et vérifier l'URL + le corps. */
    static final class ArgumentCapturingHelper {
        @SuppressWarnings("unchecked")
        static void captureAndAssert(RestTemplate restTemplate, String expectedUrl,
                                      java.util.function.Consumer<HttpEntity<?>> assertions) {
            org.mockito.ArgumentCaptor<String> urlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
            org.mockito.ArgumentCaptor<HttpEntity> entityCaptor = org.mockito.ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate).postForEntity(urlCaptor.capture(), entityCaptor.capture(), eq(Void.class));
            assertThat(urlCaptor.getValue()).isEqualTo(expectedUrl);
            assertions.accept(entityCaptor.getValue());
        }
    }
}
