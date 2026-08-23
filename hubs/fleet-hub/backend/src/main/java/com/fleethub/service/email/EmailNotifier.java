package com.fleethub.service.email;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Locale;

/**
 * Construit et envoie les emails transactionnels (bienvenue, invitation,
 * expiration d'essai, suspension, facturation). Les échecs d'envoi sont
 * loggés mais ne font jamais échouer le flux métier.
 */
@Service
@RequiredArgsConstructor
public class EmailNotifier {

    private static final Logger log = LoggerFactory.getLogger(EmailNotifier.class);
    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.FRENCH);

    private final EmailService emailService;

    public void welcome(String to, String firstName, String companyName) {
        String body = layout(
                "Bienvenue sur Fleet Hub",
                "<p>Bonjour <strong>" + esc(firstName) + "</strong>,</p>"
                        + "<p>Votre société <strong>" + esc(companyName) + "</strong> a été créée. "
                        + "Vous bénéficiez d'un essai gratuit de 14 jours sur le plan <strong>TRIAL</strong> "
                        + "(10 véhicules, 5 chauffeurs).</p>"
                        + "<p>Connectez-vous pour découvrir votre tableau de bord : <strong>coût au km</strong>, "
                        + "<strong>taux d'utilisation</strong>, <strong>conformité maintenance</strong> et "
                        + "<strong>indisponibilité imprévue</strong>.</p>");
        send(to, "Bienvenue sur Fleet Hub 🚛", body);
    }

    public void invitation(String to, String companyName, String inviteUrl, java.time.LocalDateTime expiresAt) {
        String body = layout(
                "Vous êtes invité sur Fleet Hub",
                "<p>Vous avez été invité à rejoindre la société <strong>" + esc(companyName) + "</strong> sur "
                        + "Fleet Hub, l'outil de gestion de flotte.</p>"
                        + "<p>Pour activer votre compte, cliquez sur le bouton ci-dessous "
                        + "(lien valable jusqu'au " + DATE_FMT.format(expiresAt) + ") :</p>"
                        + "<p style=\"text-align:center\"><a href=\"" + esc(inviteUrl)
                        + "\" style=\"display:inline-block;padding:12px 24px;background:#1a73e8;color:#fff;"
                        + "text-decoration:none;border-radius:6px\">Activer mon compte</a></p>"
                        + "<p style=\"color:#666;font-size:12px\">Si le bouton ne fonctionne pas, copiez ce lien : "
                        + esc(inviteUrl) + "</p>");
        send(to, "Invitation à rejoindre " + companyName, body);
    }

    public void passwordReset(String to, String resetUrl, java.time.LocalDateTime expiresAt) {
        String body = layout(
                "Réinitialisation de votre mot de passe",
                "<p>Bonjour,</p>"
                        + "<p>Une demande de réinitialisation de mot de passe a été effectuée pour ce compte "
                        + "Fleet Hub. Si vous n'êtes pas à l'origine de cette demande, ignorez cet email.</p>"
                        + "<p>Ce lien est valable jusqu'au " + DATE_FMT.format(expiresAt) + " :</p>"
                        + "<p style=\"text-align:center\"><a href=\"" + esc(resetUrl)
                        + "\" style=\"display:inline-block;padding:12px 24px;background:#1a73e8;color:#fff;"
                        + "text-decoration:none;border-radius:6px\">Choisir un nouveau mot de passe</a></p>"
                        + "<p style=\"color:#666;font-size:12px\">Si le bouton ne fonctionne pas, copiez ce lien : "
                        + esc(resetUrl) + "</p>");
        send(to, "Réinitialisation de votre mot de passe Fleet Hub", body);
    }

    public void trialExpiring(String to, String companyName, java.time.LocalDateTime trialEndsAt) {
        String body = layout(
                "Votre essai Fleet Hub se termine bientôt",
                "<p>Bonjour,</p><p>L'essai gratuit de <strong>" + esc(companyName)
                        + "</strong> expire le <strong>" + DATE_FMT.format(trialEndsAt) + "</strong>.</p>"
                        + "<p>Choisissez un abonnement (STARTER, PRO ou ENTERPRISE) pour conserver l'accès à "
                        + "vos données et à vos KPIs. Votre plan actuel : <strong>TRIAL</strong> "
                        + "(10 véhicules, 5 chauffeurs).</p>");
        send(to, "Votre essai Fleet Hub se termine bientôt", body);
    }

    public void accountSuspended(String to, String companyName) {
        String body = layout(
                "Votre compte Fleet Hub a été suspendu",
                "<p>Bonjour,</p><p>Le compte de <strong>" + esc(companyName)
                        + "</strong> a été suspendu (impayé ou manquement aux conditions d'utilisation).</p>"
                        + "<p>Contactez le support pour résoudre la situation et réactiver votre accès.</p>");
        send(to, "Votre compte Fleet Hub a été suspendu", body);
    }

    public void accountActivated(String to, String companyName) {
        String body = layout(
                "Votre compte Fleet Hub est réactivé",
                "<p>Bonjour,</p><p>Bonne nouvelle : le compte de <strong>" + esc(companyName)
                        + "</strong> a été réactivé. Vous pouvez de nouveau vous connecter.</p>");
        send(to, "Votre compte Fleet Hub est réactivé", body);
    }

    public void paymentFailed(String to, String companyName) {
        String body = layout(
                "Paiement Fleet Hub en échec",
                "<p>Bonjour,</p><p>Le dernier paiement de <strong>" + esc(companyName)
                        + "</strong> a échoué. Votre accès est suspendu jusqu'à la régularisation.</p>"
                        + "<p>Mettez à jour votre moyen de paiement depuis le portail de facturation pour "
                        + "réactiver immédiatement votre compte.</p>");
        send(to, "Échec de paiement — Fleet Hub", body);
    }

    private void send(String to, String subject, String body) {
        if (!emailService.isEnabled()) {
            log.debug("Email désactivé, envoi simulé vers {}: {}", to, subject);
            return;
        }
        try {
            emailService.send(to, subject, body);
        } catch (Exception e) {
            log.warn("Email à {} non envoyé ({}): {}", to, subject, e.getMessage());
        }
    }

    private String layout(String title, String content) {
        return "<div style=\"font-family:Arial,Helvetica,sans-serif;max-width:600px;margin:auto;"
                + "border:1px solid #e2e2e2;border-radius:8px;overflow:hidden\">"
                + "<div style=\"background:#1a2b4a;color:#fff;padding:16px 24px\"><strong>🚛 Fleet Hub</strong>"
                + " — Gestion de flotte</div>"
                + "<div style=\"padding:24px\"><h2 style=\"margin-top:0;font-size:18px\">" + title + "</h2>"
                + content + "</div>"
                + "<div style=\"background:#f6f7f9;padding:12px 24px;color:#666;font-size:12px\">"
                + "Fleet Hub — Cet email est envoyé automatiquement, merci de ne pas y répondre.</div></div>";
    }

    private String esc(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
