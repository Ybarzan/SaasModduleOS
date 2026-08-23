package com.fleethub.service.email;

/**
 * Contrat d'envoi d'email transactionnel. Deux implémentations :
 * {@link LoggingEmailService} (dev, désactivé → log) et {@link SmtpEmailService}
 * (SMTP, activé via {@code app.mail.enabled=true}).
 */
public interface EmailService {

    boolean isEnabled();

    /**
     * Envoie un email HTML.
     *
     * @throws IllegalStateException en cas d'échec d'envoi (appelant = EmailNotifier qui absorbe)
     */
    void send(String to, String subject, String htmlBody);
}
