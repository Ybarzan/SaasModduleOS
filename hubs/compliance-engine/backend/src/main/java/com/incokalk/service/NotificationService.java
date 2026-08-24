package com.incokalk.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.dto.notification.NotificationDTO;
import com.incokalk.dto.notification.NotificationRuleDTO;
import com.incokalk.dto.notification.SendNotificationDTO;
import com.incokalk.exception.ResourceNotFoundException;
import com.incokalk.model.Company;
import com.incokalk.model.CompanyRole;
import com.incokalk.model.Notification;
import com.incokalk.model.NotificationRule;
import com.incokalk.model.TrackingEvent;
import com.incokalk.model.User;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.NotificationRepository;
import com.incokalk.repository.NotificationRuleRepository;
import com.incokalk.repository.UserRepository;
import com.incokalk.rules.RuleConditionEvaluator;
import com.incokalk.rules.RuleConditionNode;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.*;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepo;
    private final NotificationRuleRepository ruleRepo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;
    private final RestTemplate restTemplate;
    private final RoleChecker roleChecker;
    private final RuleConditionEvaluator conditionEvaluator;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${incokalk.notifications.email.from-address:noreply@incokalk.com}")
    private String fromAddress;

    @Value("${incokalk.notifications.webhook.timeout:5000}")
    private int webhookTimeout;

    // ==================== Core event processing ====================

    @Transactional
    public void processEvent(SendNotificationDTO dto) {
        List<NotificationRule> rules = ruleRepo.findByCompanyIdAndEventType(
                dto.getCompanyId(), dto.getEventType());

        for (NotificationRule rule : rules) {
            if (!rule.isActive()) continue;
            if (!matchesFilters(rule, dto)) continue;

            if (Boolean.TRUE.equals(rule.getSendInApp())) {
                createInAppNotification(rule, dto);
            }
            if (Boolean.TRUE.equals(rule.getSendEmail()) && rule.getEmailRecipients() != null) {
                sendEmail(rule, dto);
            }
            if (Boolean.TRUE.equals(rule.getSendWebhook()) && rule.getWebhookUrl() != null) {
                sendWebhook(rule, dto);
            }
        }
    }

    // ==================== Convenience event methods ====================

    @Transactional
    public void onShipmentStatusChange(UUID shipmentId, String orderNumber,
                                        String oldStatus, String newStatus, UUID companyId,
                                        TrackingEvent.DataSource dataSource) {
        String title = "Statut expédition mis à jour";
        String message = String.format("La commande %s est passée de %s à %s.",
                orderNumber, oldStatus, newStatus);
        if (dataSource == TrackingEvent.DataSource.MANUAL) {
            message += " (saisie manuelle, non confirmée par un transporteur)";
        }

        processEvent(SendNotificationDTO.builder()
                .eventType("SHIPMENT_STATUS_CHANGE")
                .companyId(companyId)
                .entityType("SHIPMENT")
                .entityId(shipmentId)
                .title(title)
                .message(message)
                .templateData(Map.of(
                        "orderNumber", orderNumber,
                        "oldStatus", oldStatus,
                        "newStatus", newStatus,
                        "dataSource", dataSource != null ? dataSource.name() : ""))
                .build());
    }

    @Transactional
    public void onShipmentCreated(UUID shipmentId, String orderNumber, UUID companyId) {
        String title = "Nouvelle expédition créée";
        String message = String.format("La commande %s a été créée avec succès.", orderNumber);

        processEvent(SendNotificationDTO.builder()
                .eventType("SHIPMENT_CREATED")
                .companyId(companyId)
                .entityType("SHIPMENT")
                .entityId(shipmentId)
                .title(title)
                .message(message)
                .templateData(Map.of("orderNumber", orderNumber))
                .build());
    }

    @Transactional
    public void onQuoteReceived(int quoteCount, UUID companyId) {
        String title = "Nouveau devis reçu";
        String message = String.format("%d devis ont été reçus.", quoteCount);

        processEvent(SendNotificationDTO.builder()
                .eventType("QUOTE_RECEIVED")
                .companyId(companyId)
                .entityType("QUOTE")
                .title(title)
                .message(message)
                .templateData(Map.of("quoteCount", String.valueOf(quoteCount)))
                .build());
    }

    @Transactional
    public void onProviderDown(String providerType, UUID companyId) {
        String title = "Fournisseur indisponible";
        String message = String.format("Le fournisseur %s est actuellement indisponible.", providerType);

        processEvent(SendNotificationDTO.builder()
                .eventType("PROVIDER_DOWN")
                .companyId(companyId)
                .entityType("PROVIDER")
                .title(title)
                .message(message)
                .templateData(Map.of("providerType", providerType))
                .build());
    }

    @Transactional
    public void onProviderRecovered(String providerType, UUID companyId) {
        String title = "Fournisseur rétabli";
        String message = String.format("Le fournisseur %s est de nouveau opérationnel.", providerType);

        processEvent(SendNotificationDTO.builder()
                .eventType("PROVIDER_RECOVERED")
                .companyId(companyId)
                .entityType("PROVIDER")
                .title(title)
                .message(message)
                .templateData(Map.of("providerType", providerType))
                .build());
    }

    @Transactional
    public void onDpsAlert(UUID checkId, String checkedName, String riskLevel,
                           String matchedListName, String matchedEntryDetails, UUID companyId) {
        String title = "Alerte DPS — Correspondance détectée";
        String message = String.format(
                "Le screening de '%s' a retourné une correspondance (niveau: %s, liste: %s). %s",
                checkedName, riskLevel, matchedListName,
                matchedEntryDetails != null ? matchedEntryDetails : "");

        processEvent(SendNotificationDTO.builder()
                .eventType("DPS_ALERT")
                .companyId(companyId)
                .entityType("DENIED_PARTY_CHECK")
                .entityId(checkId)
                .title(title)
                .message(message)
                .templateData(Map.of(
                        "checkedName", checkedName,
                        "riskLevel", riskLevel,
                        "matchedListName", matchedListName != null ? matchedListName : "unknown"))
                .build());
    }

    // ==================== Rule CRUD ====================

    @Transactional(readOnly = true)
    public List<NotificationRule> listRules(UUID companyId) {
        return ruleRepo.findByCompanyId(companyId);
    }

    @Transactional(readOnly = true)
    public Page<NotificationRule> listRules(UUID companyId, Pageable pageable) {
        return ruleRepo.findByCompanyId(companyId, pageable);
    }

    @Transactional
    public NotificationRule createRule(NotificationRuleDTO dto, UUID companyId, UUID userId) {
        requireAdminForWebhook(dto, companyId, userId);

        Company company = companyRepo.findById(companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Entreprise non trouvée"));

        NotificationRule rule = NotificationRule.builder()
                .company(company)
                .name(dto.getName())
                .eventType(dto.getEventType())
                .isActive(dto.getIsActive() != null ? dto.getIsActive() : true)
                .sendEmail(dto.getSendEmail() != null ? dto.getSendEmail() : false)
                .sendWebhook(dto.getSendWebhook() != null ? dto.getSendWebhook() : false)
                .sendInApp(dto.getSendInApp() != null ? dto.getSendInApp() : true)
                .emailRecipients(dto.getEmailRecipients())
                .webhookUrl(dto.getWebhookUrl())
                .webhookSecret(dto.getWebhookSecret())
                .filterStatus(dto.getFilterStatus())
                .filterCarrierId(dto.getFilterCarrierId())
                .filterDataSource(dto.getFilterDataSource())
                .conditionJson(validateConditionJson(dto.getConditionJson()))
                .build();

        return ruleRepo.save(rule);
    }

    @Transactional
    public NotificationRule updateRule(UUID id, NotificationRuleDTO dto, UUID companyId, UUID userId) {
        requireAdminForWebhook(dto, companyId, userId);

        NotificationRule rule = ruleRepo.findById(id)
                .filter(r -> r.getCompany() != null && r.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Règle non trouvée"));

        rule.setName(dto.getName());
        rule.setEventType(dto.getEventType());
        if (dto.getIsActive() != null) rule.setActive(dto.getIsActive());
        if (dto.getSendEmail() != null) rule.setSendEmail(dto.getSendEmail());
        if (dto.getSendWebhook() != null) rule.setSendWebhook(dto.getSendWebhook());
        if (dto.getSendInApp() != null) rule.setSendInApp(dto.getSendInApp());
        rule.setEmailRecipients(dto.getEmailRecipients());
        rule.setWebhookUrl(dto.getWebhookUrl());
        rule.setWebhookSecret(dto.getWebhookSecret());
        rule.setFilterStatus(dto.getFilterStatus());
        rule.setFilterCarrierId(dto.getFilterCarrierId());
        rule.setFilterDataSource(dto.getFilterDataSource());
        rule.setConditionJson(validateConditionJson(dto.getConditionJson()));

        return ruleRepo.save(rule);
    }

    /**
     * Configurer un webhook (URL de livraison externe des événements de l'entreprise) exige au
     * moins ADMIN, même si MANAGER peut par ailleurs gérer les règles email/in-app — l'ajout
     * d'un webhook ouvre une voie d'exfiltration de données vers une URL choisie par l'appelant.
     */
    private void requireAdminForWebhook(NotificationRuleDTO dto, UUID companyId, UUID userId) {
        boolean configuresWebhook = Boolean.TRUE.equals(dto.getSendWebhook())
                || (dto.getWebhookUrl() != null && !dto.getWebhookUrl().isBlank());
        if (configuresWebhook && !roleChecker.hasRole(userId, companyId, CompanyRole.Role.ADMIN)) {
            throw new SecurityException("Seuls OWNER et ADMIN peuvent configurer un webhook");
        }
    }

    @Transactional
    public void deleteRule(UUID id, UUID companyId) {
        NotificationRule rule = ruleRepo.findById(id)
                .filter(r -> r.getCompany() != null && r.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Règle non trouvée"));
        ruleRepo.delete(rule);
    }

    // ==================== Notification management ====================

    @Transactional(readOnly = true)
    public List<Notification> listNotifications(UUID companyId, UUID userId) {
        if (userId != null) {
            return notificationRepo.findByUserIdOrderByCreatedAtDesc(userId);
        }
        return notificationRepo.findByCompanyIdOrderByCreatedAtDesc(companyId);
    }

    @Transactional(readOnly = true)
    public Page<Notification> listNotifications(UUID companyId, Pageable pageable) {
        return notificationRepo.findByCompanyIdOrderByCreatedAtDesc(companyId, pageable);
    }

    @Transactional(readOnly = true)
    public int getUnreadCount(UUID companyId, UUID userId) {
        return notificationRepo.countByCompanyIdAndUserIdUnread(companyId, userId);
    }

    @Transactional
    public void markAsRead(List<UUID> notificationIds, UUID companyId) {
        List<Notification> notifications = notificationRepo.findAllById(notificationIds);
        for (Notification n : notifications) {
            if (n.getCompany() != null && n.getCompany().getId().equals(companyId)) {
                n.setStatus("READ");
                n.setReadAt(LocalDateTime.now());
            }
        }
        notificationRepo.saveAll(notifications);
    }

    @Transactional
    public void markAllAsRead(UUID companyId, UUID userId) {
        List<Notification> unread;
        if (userId != null) {
            unread = notificationRepo.findByUserIdOrderByCreatedAtDesc(userId).stream()
                    .filter(n -> "UNREAD".equals(n.getStatus()))
                    .toList();
        } else {
            unread = notificationRepo.findByCompanyIdAndStatus(companyId, "UNREAD");
        }
        for (Notification n : unread) {
            n.setStatus("READ");
            n.setReadAt(LocalDateTime.now());
        }
        notificationRepo.saveAll(unread);
    }

    @Transactional
    public void archiveNotification(UUID id, UUID companyId) {
        Notification n = notificationRepo.findById(id)
                .filter(notif -> notif.getCompany() != null && notif.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Notification non trouvée"));
        n.setStatus("ARCHIVED");
        notificationRepo.save(n);
    }

    @Transactional
    public void deleteNotification(UUID id, UUID companyId) {
        Notification n = notificationRepo.findById(id)
                .filter(notif -> notif.getCompany() != null && notif.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Notification non trouvée"));
        notificationRepo.delete(n);
    }

    // ==================== Internal helpers ====================

    /**
     * Si la regle porte une condition structuree (conditionJson), elle remplace
     * entierement l'ancien filtrage plat -- pas de cumul des deux systemes, pour
     * eviter une semantique ambigue ("est-ce que le AND/OR structure et les
     * filtres plats doivent tous les deux matcher, ou l'un des deux ?"). Une
     * regle migre vers l'un ou l'autre, jamais les deux a la fois.
     */
    private boolean matchesFilters(NotificationRule rule, SendNotificationDTO dto) {
        if (rule.getConditionJson() != null) {
            return matchesStructuredCondition(rule, dto);
        }
        if (rule.getFilterStatus() != null && dto.getTemplateData() != null) {
            String newStatus = dto.getTemplateData().get("newStatus");
            if (newStatus != null && !rule.getFilterStatus().equals(newStatus)) {
                return false;
            }
        }
        if (rule.getFilterDataSource() != null && dto.getTemplateData() != null) {
            String dataSource = dto.getTemplateData().get("dataSource");
            // Un evenement sans dataSource (QUOTE_RECEIVED, PROVIDER_DOWN...) ne matche
            // jamais un filtre LIVE/MANUAL -- ce filtre n'a de sens que pour les evenements
            // qui portent effectivement cette information (SHIPMENT_STATUS_CHANGE).
            if (dataSource == null || !rule.getFilterDataSource().equals(dataSource)) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesStructuredCondition(NotificationRule rule, SendNotificationDTO dto) {
        try {
            RuleConditionNode condition = objectMapper.readValue(rule.getConditionJson(), RuleConditionNode.class);
            Map<String, String> context = dto.getTemplateData() != null ? dto.getTemplateData() : Map.of();
            return conditionEvaluator.evaluate(condition, context);
        } catch (Exception e) {
            // Une condition malformee ne doit jamais faire planter tout le traitement
            // des regles des autres entreprises -- elle est deja validee a la creation
            // (validateConditionJson), donc ceci ne devrait arriver qu'en cas de bug ;
            // on journalise et on considere que la regle ne matche pas, par prudence.
            log.warn("[Rules] Condition structuree invalide pour la regle {}: {}", rule.getId(), e.getMessage());
            return false;
        }
    }

    private String validateConditionJson(String conditionJson) {
        if (conditionJson == null || conditionJson.isBlank()) return null;
        try {
            RuleConditionNode node = objectMapper.readValue(conditionJson, RuleConditionNode.class);
            conditionEvaluator.validateStructure(node);
        } catch (Exception e) {
            throw new IllegalArgumentException("Condition invalide: " + e.getMessage());
        }
        return conditionJson;
    }

    private void createInAppNotification(NotificationRule rule, SendNotificationDTO dto) {
        Company company = companyRepo.getReferenceById(dto.getCompanyId());
        User user = dto.getUserId() != null ? userRepo.getReferenceById(dto.getUserId()) : null;

        Notification notification = Notification.builder()
                .company(company)
                .user(user)
                .rule(rule)
                .eventType(dto.getEventType())
                .title(dto.getTitle())
                .message(dto.getMessage())
                .channel("IN_APP")
                .status("UNREAD")
                .sentAt(LocalDateTime.now())
                .entityType(dto.getEntityType())
                .entityId(dto.getEntityId())
                .build();

        notificationRepo.save(notification);
        log.debug("[Notification] In-app créée: {} pour company={}", dto.getTitle(), dto.getCompanyId());
    }

    private void sendEmail(NotificationRule rule, SendNotificationDTO dto) {
        if (mailSender == null) {
            log.debug("[Mail] MailSender non configuré, envoi ignoré pour {}", dto.getCompanyId());
            return;
        }

        try {
            String[] recipients = rule.getEmailRecipients().split(",");
            String subject = "[IncoKalk] " + dto.getTitle();
            String htmlBody = buildEmailHtml(dto.getTitle(), dto.getMessage(), dto.getEntityType());

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(recipients);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);

            mailSender.send(message);

            Notification notification = Notification.builder()
                    .company(companyRepo.getReferenceById(dto.getCompanyId()))
                    .rule(rule)
                    .eventType(dto.getEventType())
                    .title(dto.getTitle())
                    .message(dto.getMessage())
                    .channel("EMAIL")
                    .status("READ")
                    .sentAt(LocalDateTime.now())
                    .entityType(dto.getEntityType())
                    .entityId(dto.getEntityId())
                    .build();
            notificationRepo.save(notification);

            log.info("[Mail] Email envoyé à {} pour event={}", rule.getEmailRecipients(), dto.getEventType());
        } catch (Exception e) {
            log.error("[Mail] Erreur lors de l'envoi d'email: {}", e.getMessage(), e);

            Notification notification = Notification.builder()
                    .company(companyRepo.getReferenceById(dto.getCompanyId()))
                    .rule(rule)
                    .eventType(dto.getEventType())
                    .title(dto.getTitle())
                    .message("Échec de l'envoi email: " + e.getMessage())
                    .channel("EMAIL")
                    .status("READ")
                    .sentAt(LocalDateTime.now())
                    .entityType(dto.getEntityType())
                    .entityId(dto.getEntityId())
                    .build();
            notificationRepo.save(notification);
        }
    }

    private void sendWebhook(NotificationRule rule, SendNotificationDTO dto) {
        Notification notification = Notification.builder()
                .company(companyRepo.getReferenceById(dto.getCompanyId()))
                .rule(rule)
                .eventType(dto.getEventType())
                .title(dto.getTitle())
                .message(dto.getMessage())
                .channel("WEBHOOK")
                .status("READ")
                .sentAt(LocalDateTime.now())
                .entityType(dto.getEntityType())
                .entityId(dto.getEntityId())
                .webhookStatus("PENDING")
                .build();

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("eventType", dto.getEventType());
            payload.put("title", dto.getTitle());
            payload.put("message", dto.getMessage());
            payload.put("entityType", dto.getEntityType());
            payload.put("entityId", dto.getEntityId() != null ? dto.getEntityId().toString() : null);
            payload.put("timestamp", LocalDateTime.now().toString());
            if (dto.getTemplateData() != null && dto.getTemplateData().get("dataSource") != null
                    && !dto.getTemplateData().get("dataSource").isBlank()) {
                payload.put("dataSource", dto.getTemplateData().get("dataSource"));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            if (rule.getWebhookSecret() != null && !rule.getWebhookSecret().isBlank()) {
                String signature = computeHmacSha256(
                        rule.getWebhookSecret(),
                        new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload));
                headers.set("X-Signature", signature);
            }

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(
                    rule.getWebhookUrl(), request, String.class);

            notification.setWebhookStatus("SENT");
            notification.setWebhookResponseCode(response.getStatusCode().value());

            log.info("[Webhook] Envoyé à {} avec code {}", rule.getWebhookUrl(),
                    response.getStatusCode().value());
        } catch (Exception e) {
            notification.setWebhookStatus("FAILED");
            log.error("[Webhook] Erreur lors de l'envoi vers {}: {}", rule.getWebhookUrl(), e.getMessage(), e);
        }

        notificationRepo.save(notification);
    }

    private String computeHmacSha256(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("[Webhook] Erreur calcul HMAC: {}", e.getMessage());
            return "";
        }
    }

    private String buildEmailHtml(String title, String message, String entityType) {
        String safeTitle = escapeHtml(title);
        String safeMessage = escapeHtml(message);
        String safeType = escapeHtml(entityType);
        return """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="UTF-8">
                    <style>
                        body { font-family: Arial, sans-serif; background: #f4f4f4; margin: 0; padding: 20px; }
                        .container { max-width: 600px; margin: 0 auto; background: #ffffff; border-radius: 8px; overflow: hidden; }
                        .header { background: #1a237e; color: #ffffff; padding: 20px; text-align: center; }
                        .header h1 { margin: 0; font-size: 24px; }
                        .content { padding: 30px; }
                        .content h2 { color: #1a237e; margin-top: 0; }
                        .content p { color: #333333; line-height: 1.6; }
                        .badge { display: inline-block; padding: 4px 12px; border-radius: 12px; font-size: 12px; font-weight: bold; color: #ffffff; background: #1a237e; }
                        .footer { padding: 20px; text-align: center; color: #999999; font-size: 12px; border-top: 1px solid #eeeeee; }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <div class="header">
                            <h1>IncoKalk</h1>
                        </div>
                        <div class="content">
                            <h2>%s</h2>
                            <p>%s</p>
                            <p><span class="badge">%s</span></p>
                        </div>
                        <div class="footer">
                            <p>Ce message a été envoyé automatiquement par IncoKalk.</p>
                        </div>
                    </div>
                </body>
                </html>
                """.formatted(safeTitle, safeMessage, !safeType.isEmpty() ? safeType : "NOTIFICATION");
    }

    private String escapeHtml(String input) {
        if (input == null) return "";
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;");
    }

    // ==================== Test notification ====================

    @Transactional
    public void sendTestNotification(UUID companyId) {
        String title = "Test de notification";
        String message = "Ceci est un test du système de notification IncoKalk.";

        Notification notification = Notification.builder()
                .company(companyRepo.getReferenceById(companyId))
                .eventType("TEST")
                .title(title)
                .message(message)
                .channel("IN_APP")
                .status("UNREAD")
                .sentAt(LocalDateTime.now())
                .build();

        notificationRepo.save(notification);
        log.info("[Notification] Notification de test créée pour company={}", companyId);
    }
}
