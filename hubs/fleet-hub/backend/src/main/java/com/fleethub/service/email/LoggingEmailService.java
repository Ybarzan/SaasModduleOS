package com.fleethub.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Implémentation par défaut (dev) : n'envoie rien, se contente de logger.
 * Active l'envoi SMTP en positionnant {@code app.mail.enabled=true}.
 */
@Component
@ConditionalOnProperty(name = "app.mail.enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailService.class);

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public void send(String to, String subject, String htmlBody) {
        log.info("[EMAIL][{}] Sujet: {} | Corps (extrait): {}", to, subject,
                htmlBody.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim().substring(0,
                        Math.min(120, htmlBody.length())));
    }
}
