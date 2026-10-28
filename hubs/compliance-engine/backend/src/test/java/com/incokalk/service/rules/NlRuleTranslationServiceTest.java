package com.incokalk.service.rules;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.dto.notification.NaturalLanguageRuleTranslationResult;
import com.incokalk.model.Company;
import com.incokalk.model.NotificationRule;
import com.incokalk.repository.*;
import com.incokalk.rules.RuleConditionEvaluator;
import com.incokalk.service.NotificationService;
import com.incokalk.service.RoleChecker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Exerce la VRAIE validation de NotificationService.createRule (conditionJson/
 * actionType), pas un mock qui dirait toujours oui -- c'est justement ce qui
 * doit intercepter une sortie de modèle malformée (Phase 3 J9-J10, voir
 * docs/03-plan-migration.md).
 */
@DisplayName("NlRuleTranslationService — translate")
class NlRuleTranslationServiceTest {

    RestTemplate restTemplate;
    LlmRuleTranslationConfig config;
    NotificationService notificationService;
    NlRuleTranslationService service;

    UUID companyId, userId;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);

        config = new LlmRuleTranslationConfig();
        ReflectionTestUtils.setField(config, "apiKey", "sk-ant-test");
        ReflectionTestUtils.setField(config, "baseUrl", "https://api.anthropic.com");
        ReflectionTestUtils.setField(config, "model", "claude-test-model");

        CompanyRepository companyRepo = mock(CompanyRepository.class);
        NotificationRuleRepository ruleRepo = mock(NotificationRuleRepository.class);
        companyId = UUID.randomUUID();
        userId = UUID.randomUUID();
        when(companyRepo.findById(companyId)).thenReturn(Optional.of(Company.builder().id(companyId).build()));
        when(ruleRepo.save(any())).thenAnswer(i -> i.getArgument(0));

        notificationService = new NotificationService(
                mock(NotificationRepository.class), ruleRepo, companyRepo, mock(UserRepository.class),
                mock(RestTemplate.class), mock(RoleChecker.class), new RuleConditionEvaluator(), new ObjectMapper(),
                mock(OrchestrationSuggestionRepository.class));

        service = new NlRuleTranslationService(restTemplate, config, notificationService);
    }

    private Map<String, Object> messagesApiResponse(Map<String, Object> toolInput) {
        return Map.of("content", List.of(Map.of(
                "type", "tool_use", "name", "propose_notification_rule", "input", toolInput)));
    }

    @SuppressWarnings("unchecked")
    private void mockMessagesApiCall(Map<String, Object> responseBody) {
        when(restTemplate.exchange(eq("https://api.anthropic.com/v1/messages"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(Map.class)))
                .thenReturn(new ResponseEntity<>(responseBody, org.springframework.http.HttpStatus.OK));
    }

    @Test
    @DisplayName("pas configuré (clé/modèle absents) → échec explicite, jamais d'appel HTTP")
    void translate_notConfigured_failsWithoutHttpCall() {
        ReflectionTestUtils.setField(config, "apiKey", "");

        NaturalLanguageRuleTranslationResult result = service.translate("notifie-moi à chaque retard", companyId, userId);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("non configurée");
        verifyNoInteractions(restTemplate);
    }

    @Test
    @DisplayName("traduction valide, condition simple → règle créée inactive")
    void translate_validSimpleCondition_createsInactiveDraft() {
        mockMessagesApiCall(messagesApiResponse(Map.of(
                "name", "Alerte retard DE",
                "eventType", "SHIPMENT_STATUS_CHANGE",
                "conditionJson", "{\"type\":\"LEAF\",\"field\":\"newStatus\",\"operator\":\"EQ\",\"value\":\"DELAYED\"}",
                "actionType", "NONE",
                "sendInApp", true)));

        NaturalLanguageRuleTranslationResult result = service.translate("notifie-moi quand une expédition est en retard", companyId, userId);

        assertThat(result.success()).isTrue();
        NotificationRule rule = result.rule();
        assertThat(rule.getName()).isEqualTo("Alerte retard DE");
        assertThat(rule.getEventType()).isEqualTo("SHIPMENT_STATUS_CHANGE");
        assertThat(rule.isActive()).isFalse(); // toujours, quoi qu'ait renvoyé le modèle
        assertThat(rule.getConditionJson()).contains("DELAYED");
    }

    @Test
    @DisplayName("isActive forcé à false même si le modèle (via un champ hors-schéma) suggère le contraire")
    void translate_alwaysCreatesInactiveRegardlessOfModelOutput() {
        mockMessagesApiCall(messagesApiResponse(Map.of(
                "name", "Règle", "eventType", "SHIPMENT_CREATED", "isActive", true)));

        NaturalLanguageRuleTranslationResult result = service.translate("notifie-moi à chaque nouvelle expédition", companyId, userId);

        assertThat(result.success()).isTrue();
        assertThat(result.rule().isActive()).isFalse();
    }

    @Test
    @DisplayName("conditionJson malformée proposée par le modèle → échec explicite, aucune règle créée")
    void translate_malformedConditionJson_fails() {
        mockMessagesApiCall(messagesApiResponse(Map.of(
                "name", "Règle", "eventType", "SHIPMENT_STATUS_CHANGE",
                "conditionJson", "{\"type\":\"LEAF\"}"))); // field/operator manquants

        NaturalLanguageRuleTranslationResult result = service.translate("une description ambigüe", companyId, userId);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("invalide");
        assertThat(result.rule()).isNull();
    }

    @Test
    @DisplayName("actionType inconnu proposé par le modèle → échec explicite")
    void translate_unknownActionType_fails() {
        mockMessagesApiCall(messagesApiResponse(Map.of(
                "name", "Règle", "eventType", "SHIPMENT_CREATED", "actionType", "DELETE_EVERYTHING")));

        NaturalLanguageRuleTranslationResult result = service.translate("une description", companyId, userId);

        assertThat(result.success()).isFalse();
    }

    @Test
    @DisplayName("réponse sans bloc tool_use → échec explicite")
    void translate_noToolUseBlock_fails() {
        mockMessagesApiCall(Map.of("content", List.of(Map.of("type", "text", "text", "je ne comprends pas"))));

        NaturalLanguageRuleTranslationResult result = service.translate("une description", companyId, userId);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("structurée exploitable");
    }

    @Test
    @DisplayName("appel HTTP en échec (timeout, 401...) → échec explicite, jamais de succès fictif")
    void translate_httpCallThrows_fails() {
        when(restTemplate.exchange(eq("https://api.anthropic.com/v1/messages"), eq(HttpMethod.POST),
                any(HttpEntity.class), eq(Map.class)))
                .thenThrow(new RuntimeException("401 Unauthorized"));

        NaturalLanguageRuleTranslationResult result = service.translate("une description", companyId, userId);

        assertThat(result.success()).isFalse();
        assertThat(result.errorMessage()).contains("401 Unauthorized");
    }

    @Test
    @DisplayName("sendWebhook/webhookUrl jamais renseignés depuis le modèle même si proposés (hors-schéma)")
    void translate_neverSetsWebhookFromModel() {
        mockMessagesApiCall(messagesApiResponse(Map.of(
                "name", "Règle", "eventType", "SHIPMENT_CREATED",
                "sendWebhook", true, "webhookUrl", "https://evil.example.com/hook")));

        NaturalLanguageRuleTranslationResult result = service.translate("une description", companyId, userId);

        assertThat(result.success()).isTrue();
        assertThat(result.rule().getSendWebhook()).isFalse();
        assertThat(result.rule().getWebhookUrl()).isNull();
    }

    @Test
    @DisplayName("actionType SUGGEST_ERP_ORDER_ADJUSTMENT avec budget → budget transmis, pas d'allowedCarrierIds")
    void translate_erpAdjustmentWithBudget_neverSetsAllowedCarrierIds() {
        mockMessagesApiCall(messagesApiResponse(Map.of(
                "name", "Ajustement ERP", "eventType", "SHIPMENT_STATUS_CHANGE",
                "actionType", "SUGGEST_ERP_ORDER_ADJUSTMENT", "maxBudgetAmount", 5000)));

        NaturalLanguageRuleTranslationResult result = service.translate("ajuste la commande si le budget dépasse 5000€", companyId, userId);

        assertThat(result.success()).isTrue();
        assertThat(result.rule().getActionType()).isEqualTo("SUGGEST_ERP_ORDER_ADJUSTMENT");
        assertThat(result.rule().getMaxBudgetAmount()).isEqualByComparingTo("5000");
        assertThat(result.rule().getAllowedCarrierIds()).isNull();
    }
}
