package com.incokalk.service.rules;

import com.incokalk.dto.notification.NaturalLanguageRuleTranslationResult;
import com.incokalk.dto.notification.NotificationRuleDTO;
import com.incokalk.model.NotificationRule;
import com.incokalk.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Traduit une description en langage naturel en {@link NotificationRule},
 * via un appel outil (tool use) à l'API Messages d'Anthropic -- Phase 3
 * J9-J10 (docs/03-plan-migration.md). Jamais testé contre un vrai modèle
 * depuis cet environnement (aucune clé API applicative disponible ici,
 * volontairement distincte de l'accès dont dispose l'agent qui a écrit ce
 * code) -- la forme de la requête/réponse Messages API (tools/tool_choice/
 * tool_use) est en revanche stable et documentée depuis longtemps.
 * <p>
 * La règle produite est TOUJOURS créée avec {@code isActive=false}, quoi que
 * le modèle propose -- validation humaine obligatoire avant activation, sans
 * exception possible depuis cette méthode. Le schéma d'outil exclut
 * délibérément {@code allowedCarrierIds}/{@code webhookUrl}/{@code webhookSecret} :
 * le modèle n'a aucun moyen de connaître de vrais UUID de transporteur
 * fleet-hub ni une vraie URL de webhook -- les deviner produirait une règle
 * qui a l'air configurée mais ne correspond à rien de réel.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NlRuleTranslationService {

    private static final String TOOL_NAME = "propose_notification_rule";
    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestTemplate restTemplate;
    private final LlmRuleTranslationConfig config;
    private final NotificationService notificationService;

    public NaturalLanguageRuleTranslationResult translate(String description, UUID companyId, UUID userId) {
        if (!config.isConfigured()) {
            return NaturalLanguageRuleTranslationResult.fail(
                    "Traduction en langage naturel non configurée (incokalk.rules.nl-translation.api-key/model absents)");
        }

        Map<String, Object> toolInput;
        try {
            Map<String, Object> response = callMessagesApi(description);
            toolInput = extractToolInput(response);
        } catch (Exception e) {
            log.error("[NlRuleTranslation] Appel au modèle échoué: {}", e.getMessage());
            return NaturalLanguageRuleTranslationResult.fail("Erreur lors de l'appel au modèle : " + e.getMessage());
        }

        if (toolInput == null) {
            return NaturalLanguageRuleTranslationResult.fail(
                    "Le modèle n'a pas produit de règle structurée exploitable pour cette description");
        }

        try {
            NotificationRuleDTO dto = toDto(toolInput);
            NotificationRule draft = notificationService.createRule(dto, companyId, userId);
            log.info("[NlRuleTranslation] Règle brouillon {} créée (isActive=false) depuis une description en langage naturel", draft.getId());
            return NaturalLanguageRuleTranslationResult.ok(draft);
        } catch (IllegalArgumentException e) {
            return NaturalLanguageRuleTranslationResult.fail("Règle invalide proposée par le modèle : " + e.getMessage());
        }
    }

    private Map<String, Object> callMessagesApi(String description) {
        Map<String, Object> tool = Map.of(
                "name", TOOL_NAME,
                "description", "Propose une règle de notification structurée à partir d'une description en langage naturel.",
                "input_schema", toolInputSchema());

        Map<String, Object> body = Map.of(
                "model", config.getModel(),
                "max_tokens", 1024,
                "system", systemPrompt(),
                "tools", List.of(tool),
                "tool_choice", Map.of("type", "tool", "name", TOOL_NAME),
                "messages", List.of(Map.of("role", "user", "content", description)));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-api-key", config.getApiKey());
        headers.set("anthropic-version", ANTHROPIC_VERSION);

        ResponseEntity<Map> response = restTemplate.exchange(
                config.getBaseUrl() + "/v1/messages", HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        return response.getBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractToolInput(Map<String, Object> response) {
        if (response == null) return null;
        List<Map<String, Object>> content = (List<Map<String, Object>>) response.get("content");
        if (content == null) return null;
        for (Map<String, Object> block : content) {
            if (TOOL_NAME.equals(block.get("name")) && "tool_use".equals(block.get("type"))) {
                return (Map<String, Object>) block.get("input");
            }
        }
        return null;
    }

    private NotificationRuleDTO toDto(Map<String, Object> toolInput) {
        NotificationRuleDTO.NotificationRuleDTOBuilder builder = NotificationRuleDTO.builder()
                .name((String) toolInput.get("name"))
                .eventType((String) toolInput.get("eventType"))
                .conditionJson((String) toolInput.get("conditionJson"))
                .actionType((String) toolInput.get("actionType"))
                .sendEmail((Boolean) toolInput.get("sendEmail"))
                .sendWebhook(Boolean.FALSE) // jamais depuis le modele : pas de vraie URL de webhook a sa disposition
                .sendInApp(toolInput.get("sendInApp") != null ? (Boolean) toolInput.get("sendInApp") : Boolean.TRUE)
                .emailRecipients((String) toolInput.get("emailRecipients"));

        Object maxBudget = toolInput.get("maxBudgetAmount");
        if (maxBudget instanceof Number n) {
            builder.maxBudgetAmount(BigDecimal.valueOf(n.doubleValue()));
        }

        // Jamais actif sans relecture humaine -- voir le javadoc de classe. Ecrase
        // systematiquement ce que le modele a pu renvoyer, meme s'il n'a pas ce champ
        // dans son schema d'outil (defense en profondeur).
        builder.isActive(Boolean.FALSE);

        return builder.build();
    }

    private String systemPrompt() {
        return """
                Tu traduis une description en langage naturel, écrite par un utilisateur non-développeur, \
                en une règle de notification structurée pour une plateforme de suivi d'expéditions internationales.

                Types d'événement disponibles (eventType), avec les champs de contexte réellement disponibles \
                pour chacun (à utiliser dans conditionJson) :
                - SHIPMENT_STATUS_CHANGE : orderNumber, oldStatus, newStatus, dataSource (LIVE ou MANUAL)
                - SHIPMENT_CREATED : orderNumber uniquement

                conditionJson (optionnel) est un arbre sérialisé en chaîne JSON, grammaire :
                {"type":"AND"|"OR","children":[...]} ou {"type":"LEAF","field":"...","operator":"EQ"|"NEQ"|"CONTAINS"|"IN"|"GT"|"GTE"|"LT"|"LTE","value":"..."}
                Omets ce champ si la description ne demande aucune condition spécifique (notification sur tout événement de ce type).

                actionType : "SUGGEST_ERP_ORDER_ADJUSTMENT" UNIQUEMENT si la description demande explicitement \
                qu'un ajustement de commande ERP soit proposé en cas de dérive -- sinon "NONE" ou omis. \
                N'invente JAMAIS d'identifiant de transporteur : ce champ n'est pas dans ton schéma d'outil, \
                un humain le configurera après relecture.

                maxBudgetAmount : uniquement si actionType=SUGGEST_ERP_ORDER_ADJUSTMENT ET que la description \
                mentionne explicitement un montant.

                Sois conservateur : en cas de doute sur une condition ou une valeur, omets-la plutôt que de deviner.
                """;
    }

    private Map<String, Object> toolInputSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.ofEntries(
                        Map.entry("name", Map.of("type", "string", "description", "Nom court et descriptif de la règle")),
                        Map.entry("eventType", Map.of("type", "string", "enum", List.of("SHIPMENT_STATUS_CHANGE", "SHIPMENT_CREATED"))),
                        Map.entry("conditionJson", Map.of("type", "string",
                                "description", "Arbre de condition RuleConditionNode sérialisé en chaîne JSON, voir le system prompt pour la grammaire. Omis si aucune condition spécifique.")),
                        Map.entry("actionType", Map.of("type", "string", "enum", List.of("NONE", "SUGGEST_ERP_ORDER_ADJUSTMENT"))),
                        Map.entry("maxBudgetAmount", Map.of("type", "number", "description", "Plafond de budget en euros, seulement si actionType=SUGGEST_ERP_ORDER_ADJUSTMENT")),
                        Map.entry("sendEmail", Map.of("type", "boolean")),
                        Map.entry("sendInApp", Map.of("type", "boolean")),
                        Map.entry("emailRecipients", Map.of("type", "string", "description", "Adresses email séparées par des virgules, seulement si sendEmail=true et mentionnées explicitement"))
                ),
                "required", List.of("name", "eventType"));
    }
}
