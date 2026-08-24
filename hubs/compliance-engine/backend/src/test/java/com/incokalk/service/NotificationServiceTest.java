package com.incokalk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.dto.notification.NotificationRuleDTO;
import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.*;
import com.incokalk.repository.*;
import com.incokalk.rules.RuleConditionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("NotificationService — Tests unitaires")
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepo;
    @Mock NotificationRuleRepository ruleRepo;
    @Mock CompanyRepository companyRepo;
    @Mock UserRepository userRepo;
    @Mock RestTemplate restTemplate;
    @Mock RoleChecker roleChecker;
    // Instances reelles (pas de mock) : on veut tester la vraie evaluation JSON -> condition,
    // pas verifier des appels sur un double.
    @Spy RuleConditionEvaluator conditionEvaluator = new RuleConditionEvaluator();
    @Spy ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks NotificationService service;

    private UUID companyId, userId, ruleId, notificationId;
    private Company company;
    private User user;
    private NotificationRule rule;
    private Notification notification;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(service, "fromAddress", "test@test.com");
        ReflectionTestUtils.setField(service, "webhookTimeout", 5000);

        companyId = UUID.randomUUID();
        userId = UUID.randomUUID();
        ruleId = UUID.randomUUID();
        notificationId = UUID.randomUUID();

        company = Company.builder().id(companyId).name("TestCo").build();
        user = User.builder().id(userId).email("user@test.com").fullName("User").company(company).build();
        rule = NotificationRule.builder()
                .id(ruleId)
                .company(company)
                .name("Règle test")
                .eventType("SHIPMENT_STATUS_CHANGE")
                .isActive(true)
                .sendInApp(true)
                .sendEmail(false)
                .sendWebhook(false)
                .build();
        notification = Notification.builder()
                .id(notificationId)
                .company(company)
                .user(user)
                .title("Test notif")
                .message("Message test")
                .status("UNREAD")
                .eventType("TEST")
                .channel("IN_APP")
                .build();
    }

    private NotificationRuleDTO buildRuleDto() {
        return NotificationRuleDTO.builder()
                .name("Règle test")
                .eventType("SHIPMENT_STATUS_CHANGE")
                .isActive(true)
                .sendInApp(true)
                .sendEmail(false)
                .sendWebhook(false)
                .build();
    }

    // ── createRule ────────────────────────────────────────────────────

    @Test
    @DisplayName("Création de règle réussie")
    void createRule_success() {
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(ruleRepo.save(any(NotificationRule.class))).thenAnswer(i -> {
            NotificationRule r = i.getArgument(0);
            r.setId(ruleId);
            return r;
        });

        NotificationRule result = service.createRule(buildRuleDto(), companyId, userId);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("Règle test");
        assertThat(result.getCompany()).isEqualTo(company);
        assertThat(result.isActive()).isTrue();
    }

    @Test
    @DisplayName("Création de règle avec entreprise introuvable → exception")
    void createRule_companyNotFound_throws() {
        when(companyRepo.findById(companyId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createRule(buildRuleDto(), companyId, userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Entreprise non trouvée");
    }

    @Test
    @DisplayName("Création de règle avec webhook par un MANAGER → 403")
    void createRule_webhookByManager_throwsForbidden() {
        NotificationRuleDTO dto = NotificationRuleDTO.builder()
                .name("Règle webhook")
                .eventType("SHIPMENT_STATUS_CHANGE")
                .sendWebhook(true)
                .webhookUrl("https://client.example.com/hook")
                .build();
        when(roleChecker.hasRole(userId, companyId, CompanyRole.Role.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> service.createRule(dto, companyId, userId))
                .isInstanceOf(SecurityException.class);

        verify(companyRepo, never()).findById(any());
        verify(ruleRepo, never()).save(any());
    }

    @Test
    @DisplayName("Création de règle avec webhook par un ADMIN → autorisé")
    void createRule_webhookByAdmin_succeeds() {
        NotificationRuleDTO dto = NotificationRuleDTO.builder()
                .name("Règle webhook")
                .eventType("SHIPMENT_STATUS_CHANGE")
                .sendWebhook(true)
                .webhookUrl("https://client.example.com/hook")
                .build();
        when(roleChecker.hasRole(userId, companyId, CompanyRole.Role.ADMIN)).thenReturn(true);
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(ruleRepo.save(any(NotificationRule.class))).thenAnswer(i -> i.getArgument(0));

        NotificationRule result = service.createRule(dto, companyId, userId);

        assertThat(result.getWebhookUrl()).isEqualTo("https://client.example.com/hook");
    }

    // ── updateRule ────────────────────────────────────────────────────

    @Test
    @DisplayName("Mise à jour de règle réussie")
    void updateRule_success() {
        NotificationRuleDTO dto = NotificationRuleDTO.builder()
                .name("Règle modifiée")
                .eventType("SHIPMENT_CREATED")
                .isActive(true)
                .sendInApp(true)
                .build();
        when(ruleRepo.findById(ruleId)).thenReturn(Optional.of(rule));
        when(ruleRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        NotificationRule result = service.updateRule(ruleId, dto, companyId, userId);

        assertThat(result.getName()).isEqualTo("Règle modifiée");
        assertThat(result.getEventType()).isEqualTo("SHIPMENT_CREATED");
    }

    @Test
    @DisplayName("Mise à jour de règle introuvable → exception")
    void updateRule_notFound_throws() {
        when(ruleRepo.findById(ruleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateRule(ruleId, buildRuleDto(), companyId, userId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Règle non trouvée");
    }

    @Test
    @DisplayName("Mise à jour de règle avec webhook par un MANAGER → 403")
    void updateRule_webhookByManager_throwsForbidden() {
        NotificationRuleDTO dto = NotificationRuleDTO.builder()
                .name("Règle modifiée")
                .eventType("SHIPMENT_CREATED")
                .sendWebhook(true)
                .webhookUrl("https://client.example.com/hook")
                .build();
        when(roleChecker.hasRole(userId, companyId, CompanyRole.Role.ADMIN)).thenReturn(false);

        assertThatThrownBy(() -> service.updateRule(ruleId, dto, companyId, userId))
                .isInstanceOf(SecurityException.class);

        verify(ruleRepo, never()).findById(any());
        verify(ruleRepo, never()).save(any());
    }

    // ── deleteRule ────────────────────────────────────────────────────

    @Test
    @DisplayName("Suppression de règle réussie")
    void deleteRule_success() {
        when(ruleRepo.findById(ruleId)).thenReturn(Optional.of(rule));

        service.deleteRule(ruleId, companyId);

        verify(ruleRepo).delete(rule);
    }

    @Test
    @DisplayName("Suppression de règle introuvable → exception")
    void deleteRule_notFound_throws() {
        when(ruleRepo.findById(ruleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteRule(ruleId, companyId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(ruleRepo, never()).delete(any());
    }

    // ── listNotifications ─────────────────────────────────────────────

    @Test
    @DisplayName("Liste des notifications par utilisateur")
    void listNotifications_byUser() {
        when(notificationRepo.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(notification));

        List<Notification> result = service.listNotifications(companyId, userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Test notif");
    }

    @Test
    @DisplayName("Liste des notifications par entreprise (userId null)")
    void listNotifications_byCompany() {
        when(notificationRepo.findByCompanyIdOrderByCreatedAtDesc(companyId)).thenReturn(List.of(notification));

        List<Notification> result = service.listNotifications(companyId, (UUID) null);

        assertThat(result).hasSize(1);
    }

    // ── getUnreadCount ────────────────────────────────────────────────

    @Test
    @DisplayName("Nombre de notifications non lues")
    void getUnreadCount() {
        when(notificationRepo.countByCompanyIdAndUserIdUnread(companyId, userId)).thenReturn(5);

        int result = service.getUnreadCount(companyId, userId);

        assertThat(result).isEqualTo(5);
    }

    // ── markAsRead ────────────────────────────────────────────────────

    @Test
    @DisplayName("Marquer des notifications comme lues")
    void markAsRead() {
        List<UUID> ids = List.of(notificationId);
        when(notificationRepo.findAllById(ids)).thenReturn(List.of(notification));
        when(notificationRepo.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        service.markAsRead(ids, companyId);

        assertThat(notification.getStatus()).isEqualTo("READ");
        assertThat(notification.getReadAt()).isNotNull();
    }

    // ── markAllAsRead ─────────────────────────────────────────────────

    @Test
    @DisplayName("Marquer toutes les notifications comme lues (par utilisateur)")
    void markAllAsRead_byUser() {
        when(notificationRepo.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(notification));
        when(notificationRepo.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        service.markAllAsRead(companyId, userId);

        assertThat(notification.getStatus()).isEqualTo("READ");
        assertThat(notification.getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("Marquer toutes les notifications comme lues (par entreprise)")
    void markAllAsRead_byCompany() {
        when(notificationRepo.findByCompanyIdAndStatus(companyId, "UNREAD")).thenReturn(List.of(notification));
        when(notificationRepo.saveAll(any())).thenAnswer(i -> i.getArgument(0));

        service.markAllAsRead(companyId, null);

        assertThat(notification.getStatus()).isEqualTo("READ");
    }

    // ── archiveNotification ───────────────────────────────────────────

    @Test
    @DisplayName("Archivage de notification réussie")
    void archiveNotification_success() {
        when(notificationRepo.findById(notificationId)).thenReturn(Optional.of(notification));
        when(notificationRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.archiveNotification(notificationId, companyId);

        assertThat(notification.getStatus()).isEqualTo("ARCHIVED");
    }

    @Test
    @DisplayName("Archivage de notification introuvable → exception")
    void archiveNotification_notFound_throws() {
        when(notificationRepo.findById(notificationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.archiveNotification(notificationId, companyId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Notification non trouvée");
    }

    // ── deleteNotification ────────────────────────────────────────────

    @Test
    @DisplayName("Suppression de notification réussie")
    void deleteNotification_success() {
        when(notificationRepo.findById(notificationId)).thenReturn(Optional.of(notification));

        service.deleteNotification(notificationId, companyId);

        verify(notificationRepo).delete(notification);
    }

    @Test
    @DisplayName("Suppression de notification introuvable → exception")
    void deleteNotification_notFound_throws() {
        when(notificationRepo.findById(notificationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteNotification(notificationId, companyId))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(notificationRepo, never()).delete(any());
    }

    // ── sendTestNotification ──────────────────────────────────────────

    @Test
    @DisplayName("Envoi de notification de test")
    void sendTestNotification() {
        when(companyRepo.getReferenceById(companyId)).thenReturn(company);
        when(notificationRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.sendTestNotification(companyId);

        verify(notificationRepo).save(argThat(n ->
                "TEST".equals(n.getEventType()) &&
                "IN_APP".equals(n.getChannel()) &&
                "UNREAD".equals(n.getStatus()) &&
                n.getCompany().equals(company)
        ));
    }

    // ── Sécurité : non-exposition du secret webhook ──────────────────

    @Test
    @DisplayName("Le secret webhook n'est jamais sérialisé dans la réponse JSON")
    void webhookSecret_neverSerialized() throws Exception {
        NotificationRule webhookRule = NotificationRule.builder()
                .id(ruleId)
                .company(company)
                .name("Règle webhook")
                .eventType("SHIPMENT_STATUS_CHANGE")
                .isActive(true)
                .sendWebhook(true)
                .webhookUrl("https://client.example.com/hook")
                .webhookSecret("super-secret-hmac-key")
                .build();

        String json = new ObjectMapper().writeValueAsString(webhookRule);

        assertThat(json).doesNotContain("super-secret-hmac-key");
        assertThat(json).doesNotContain("webhookSecret");
        assertThat(json).contains("https://client.example.com/hook");
    }

    // ── onShipmentCreated ─────────────────────────────────────────────

    @Test
    @DisplayName("Événement expédition créée — aucune règle → pas de notif")
    void onShipmentCreated_noRules_noOp() {
        UUID shipmentId = UUID.randomUUID();
        when(ruleRepo.findByCompanyIdAndEventType(companyId, "SHIPMENT_CREATED")).thenReturn(Collections.emptyList());

        service.onShipmentCreated(shipmentId, "SHP-001", companyId);

        verify(notificationRepo, never()).save(any());
    }

    @Test
    @DisplayName("Événement expédition créée — règle active avec in-app")
    void onShipmentCreated_withRule_createsNotification() {
        UUID shipmentId = UUID.randomUUID();
        when(ruleRepo.findByCompanyIdAndEventType(companyId, "SHIPMENT_CREATED")).thenReturn(List.of(rule));
        when(companyRepo.getReferenceById(companyId)).thenReturn(company);
        when(notificationRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.onShipmentCreated(shipmentId, "SHP-001", companyId);

        verify(notificationRepo).save(argThat(n ->
                "SHIPMENT_CREATED".equals(n.getEventType()) &&
                n.getTitle().contains("Nouvelle expédition")
        ));
    }

    // ── onShipmentStatusChange / provenance LIVE-MANUAL ────────────────

    @Test
    @DisplayName("Changement de statut MANUAL — le message signale la saisie manuelle")
    void onShipmentStatusChange_manual_appendsNoteToMessage() {
        UUID shipmentId = UUID.randomUUID();
        when(ruleRepo.findByCompanyIdAndEventType(companyId, "SHIPMENT_STATUS_CHANGE")).thenReturn(List.of(rule));
        when(companyRepo.getReferenceById(companyId)).thenReturn(company);
        when(notificationRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.onShipmentStatusChange(shipmentId, "SHP-001", "BOOKED", "IN_TRANSIT", companyId,
                TrackingEvent.DataSource.MANUAL);

        verify(notificationRepo).save(argThat(n -> n.getMessage().contains("saisie manuelle")));
    }

    @Test
    @DisplayName("Changement de statut LIVE — pas de mention de saisie manuelle dans le message")
    void onShipmentStatusChange_live_noManualNote() {
        UUID shipmentId = UUID.randomUUID();
        when(ruleRepo.findByCompanyIdAndEventType(companyId, "SHIPMENT_STATUS_CHANGE")).thenReturn(List.of(rule));
        when(companyRepo.getReferenceById(companyId)).thenReturn(company);
        when(notificationRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.onShipmentStatusChange(shipmentId, "SHP-001", "BOOKED", "IN_TRANSIT", companyId,
                TrackingEvent.DataSource.LIVE);

        verify(notificationRepo).save(argThat(n -> !n.getMessage().contains("saisie manuelle")));
    }

    @Test
    @DisplayName("filterDataSource=LIVE — laisse passer un événement LIVE")
    void onShipmentStatusChange_filterDataSourceLive_matchesLiveEvent() {
        UUID shipmentId = UUID.randomUUID();
        rule.setFilterDataSource("LIVE");
        when(ruleRepo.findByCompanyIdAndEventType(companyId, "SHIPMENT_STATUS_CHANGE")).thenReturn(List.of(rule));
        when(companyRepo.getReferenceById(companyId)).thenReturn(company);
        when(notificationRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.onShipmentStatusChange(shipmentId, "SHP-001", "BOOKED", "IN_TRANSIT", companyId,
                TrackingEvent.DataSource.LIVE);

        verify(notificationRepo).save(any());
    }

    @Test
    @DisplayName("filterDataSource=LIVE — bloque un événement MANUAL (règle « pas d'alerte sur saisie manuelle »)")
    void onShipmentStatusChange_filterDataSourceLive_skipsManualEvent() {
        UUID shipmentId = UUID.randomUUID();
        rule.setFilterDataSource("LIVE");
        when(ruleRepo.findByCompanyIdAndEventType(companyId, "SHIPMENT_STATUS_CHANGE")).thenReturn(List.of(rule));

        service.onShipmentStatusChange(shipmentId, "SHP-001", "BOOKED", "IN_TRANSIT", companyId,
                TrackingEvent.DataSource.MANUAL);

        verify(notificationRepo, never()).save(any());
    }

    // ── condition structurée (conditionJson) ────────────────────────────

    @Test
    @DisplayName("createRule : condition JSON valide -> persistée telle quelle")
    void createRule_validConditionJson_persisted() {
        String json = """
                {"type":"LEAF","field":"newStatus","operator":"EQ","value":"IN_TRANSIT"}""";
        NotificationRuleDTO dto = NotificationRuleDTO.builder()
                .name("Règle test").eventType("SHIPMENT_STATUS_CHANGE").isActive(true)
                .sendInApp(true).sendEmail(false).sendWebhook(false)
                .conditionJson(json).build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));
        when(ruleRepo.save(any(NotificationRule.class))).thenAnswer(i -> i.getArgument(0));

        NotificationRule result = service.createRule(dto, companyId, userId);

        assertThat(result.getConditionJson()).isEqualTo(json);
    }

    @Test
    @DisplayName("createRule : condition JSON invalide (opérateur inconnu) -> rejetée")
    void createRule_invalidConditionJson_rejected() {
        String json = """
                {"type":"LEAF","field":"newStatus","operator":"NOT_AN_OPERATOR","value":"IN_TRANSIT"}""";
        NotificationRuleDTO dto = NotificationRuleDTO.builder()
                .name("Règle test").eventType("SHIPMENT_STATUS_CHANGE").isActive(true)
                .sendInApp(true).sendEmail(false).sendWebhook(false)
                .conditionJson(json).build();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(company));

        assertThatThrownBy(() -> service.createRule(dto, companyId, userId))
                .isInstanceOf(IllegalArgumentException.class);
        verify(ruleRepo, never()).save(any());
    }

    @Test
    @DisplayName("condition structurée AND — les deux branches matchent -> notifie")
    void onShipmentStatusChange_structuredAnd_bothMatch_notifies() {
        rule.setConditionJson("""
                {"type":"AND","children":[
                  {"type":"LEAF","field":"newStatus","operator":"EQ","value":"IN_TRANSIT"},
                  {"type":"LEAF","field":"dataSource","operator":"EQ","value":"LIVE"}
                ]}""");
        when(ruleRepo.findByCompanyIdAndEventType(companyId, "SHIPMENT_STATUS_CHANGE")).thenReturn(List.of(rule));
        when(companyRepo.getReferenceById(companyId)).thenReturn(company);
        when(notificationRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.onShipmentStatusChange(UUID.randomUUID(), "SHP-001", "BOOKED", "IN_TRANSIT", companyId,
                TrackingEvent.DataSource.LIVE);

        verify(notificationRepo).save(any());
    }

    @Test
    @DisplayName("condition structurée AND — une seule branche matche -> ne notifie pas")
    void onShipmentStatusChange_structuredAnd_partialMatch_skips() {
        rule.setConditionJson("""
                {"type":"AND","children":[
                  {"type":"LEAF","field":"newStatus","operator":"EQ","value":"IN_TRANSIT"},
                  {"type":"LEAF","field":"dataSource","operator":"EQ","value":"LIVE"}
                ]}""");
        when(ruleRepo.findByCompanyIdAndEventType(companyId, "SHIPMENT_STATUS_CHANGE")).thenReturn(List.of(rule));

        service.onShipmentStatusChange(UUID.randomUUID(), "SHP-001", "BOOKED", "IN_TRANSIT", companyId,
                TrackingEvent.DataSource.MANUAL);

        verify(notificationRepo, never()).save(any());
    }

    @Test
    @DisplayName("condition structurée OR — une branche matche -> notifie")
    void onShipmentStatusChange_structuredOr_oneMatches_notifies() {
        rule.setConditionJson("""
                {"type":"OR","children":[
                  {"type":"LEAF","field":"newStatus","operator":"EQ","value":"DELIVERED"},
                  {"type":"LEAF","field":"newStatus","operator":"EQ","value":"IN_TRANSIT"}
                ]}""");
        when(ruleRepo.findByCompanyIdAndEventType(companyId, "SHIPMENT_STATUS_CHANGE")).thenReturn(List.of(rule));
        when(companyRepo.getReferenceById(companyId)).thenReturn(company);
        when(notificationRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.onShipmentStatusChange(UUID.randomUUID(), "SHP-001", "BOOKED", "IN_TRANSIT", companyId,
                TrackingEvent.DataSource.LIVE);

        verify(notificationRepo).save(any());
    }

    @Test
    @DisplayName("condition structurée présente -> ignore filterStatus/filterDataSource (pas de cumul des deux systèmes)")
    void onShipmentStatusChange_structuredConditionOverridesLegacyFilters() {
        // filterStatus exigerait DELIVERED (ne matcherait pas IN_TRANSIT), mais la
        // condition structuree accepte explicitement IN_TRANSIT -- elle doit gagner.
        rule.setFilterStatus("DELIVERED");
        rule.setConditionJson("""
                {"type":"LEAF","field":"newStatus","operator":"EQ","value":"IN_TRANSIT"}""");
        when(ruleRepo.findByCompanyIdAndEventType(companyId, "SHIPMENT_STATUS_CHANGE")).thenReturn(List.of(rule));
        when(companyRepo.getReferenceById(companyId)).thenReturn(company);
        when(notificationRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        service.onShipmentStatusChange(UUID.randomUUID(), "SHP-001", "BOOKED", "IN_TRANSIT", companyId,
                TrackingEvent.DataSource.LIVE);

        verify(notificationRepo).save(any());
    }
}
