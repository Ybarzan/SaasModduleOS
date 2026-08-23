package com.incokalk.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${incokalk.email.from-address:noreply@incokalk.com}")
    private String fromAddress;

    @Value("${incokalk.app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    public void sendPasswordReset(String to, String resetToken) {
        String resetUrl = frontendUrl + "/reset-password?token=" + resetToken;
        String body = """
            <p>Bonjour,</p>
            <p>Une demande de réinitialisation de votre mot de passe a été effectuée.</p>
            <p>Cliquez sur le bouton ci-dessous pour choisir un nouveau mot de passe :</p>
            <table role="presentation" style="margin: 24px 0;">
              <tr><td style="background-color: #2563eb; border-radius: 8px; text-align: center;">
                <a href="%s" style="display: inline-block; padding: 12px 24px; color: #ffffff; text-decoration: none; font-weight: 600;">Réinitialiser mon mot de passe</a>
              </td></tr>
            </table>
            <p style="color: #6b7280; font-size: 13px;">Ce lien expire dans 1 heure. Si vous n'êtes pas à l'origine de cette demande, ignorez cet email.</p>
            """.formatted(resetUrl);
        sendHtml(to, "Réinitialisation de votre mot de passe", "Réinitialisation de votre mot de passe", body);
    }

    public void sendTeamInvitation(String to, String tempPassword, String companyName) {
        String loginUrl = frontendUrl + "/login";
        String body = """
            <p>Bonjour,</p>
            <p>Vous avez été invité(e) à rejoindre l'équipe <strong>%s</strong> sur IncoKalk.</p>
            <p>Voici vos identifiants temporaires :</p>
            <table style="margin: 16px 0; border: 1px solid #e5e7eb; border-radius: 8px;">
              <tr><td style="padding: 8px 16px; color: #6b7280;">Email</td><td style="padding: 8px 16px; font-weight: 600;">%s</td></tr>
              <tr><td style="padding: 8px 16px; color: #6b7280;">Mot de passe</td><td style="padding: 8px 16px; font-weight: 600;">%s</td></tr>
            </table>
            <p>Connectez-vous puis changez ce mot de passe depuis votre profil.</p>
            <table role="presentation" style="margin: 24px 0;">
              <tr><td style="background-color: #2563eb; border-radius: 8px; text-align: center;">
                <a href="%s" style="display: inline-block; padding: 12px 24px; color: #ffffff; text-decoration: none; font-weight: 600;">Se connecter</a>
              </td></tr>
            </table>
            """.formatted(companyName, to, tempPassword, loginUrl);
        sendHtml(to, "Invitation à rejoindre " + companyName, "Vous avez été invité(e) sur IncoKalk", body);
    }

    /**
     * Email de bienvenue à l'inscription -- premier maillon d'une séquence
     * d'onboarding (item marketing #16), pas encore une vraie séquence
     * multi-étapes espacée dans le temps (jour 3, jour 7...), qui demanderait
     * un job planifié suivant la date d'inscription -- hors scope ici, juste
     * cet email "jour 0" envoyé de façon synchrone, comme sendTeamInvitation.
     * Le contenu reprend le mécanisme déjà articulé sur la page tarifs : le
     * signal d'upgrade est un rôle assigné (douane, entrepôt), pas une taille
     * d'entreprise à deviner.
     */
    public void sendWelcomeEmail(String to, String fullName) {
        String dashboardUrl = frontendUrl + "/dashboard";
        String simulationUrl = frontendUrl + "/simulation";
        // User n'a qu'un champ fullName (pas de prénom/nom séparés) -- on ne
        // prend que le premier mot pour une salutation courte, "Bonjour" seul
        // si le nom est absent.
        String firstToken = (fullName != null && !fullName.isBlank()) ? fullName.trim().split("\\s+")[0] : null;
        String greeting = firstToken != null ? "Bonjour " + firstToken : "Bonjour";
        String body = """
            <p>%s,</p>
            <p>Bienvenue sur IncoKalk. Votre compte est prêt — voici par où commencer.</p>
            <p><strong>Le Calculateur Incoterms</strong> est le point d'entrée le plus rapide : simulez le coût complet d'une expédition (transport, douane, assurance) selon les 11 Incoterms 2020.</p>
            <table role="presentation" style="margin: 24px 0;">
              <tr><td style="background-color: #2563eb; border-radius: 8px; text-align: center;">
                <a href="%s" style="display: inline-block; padding: 12px 24px; color: #ffffff; text-decoration: none; font-weight: 600;">Essayer le calculateur</a>
              </td></tr>
            </table>
            <p style="color: #6b7280; font-size: 13px;">
              Pas besoin de deviner quand passer à un plan supérieur : le jour où vous créez un rôle
              dédié à la douane ou à l'entrepôt et l'assignez à quelqu'un, le module correspondant
              devient pertinent — pas avant, pas pour toute l'équipe d'un coup.
            </p>
            <p style="color: #6b7280; font-size: 13px;">
              Retrouvez votre tableau de bord à tout moment : <a href="%s">%s</a>
            </p>
            """.formatted(greeting, simulationUrl, dashboardUrl, dashboardUrl);
        sendHtml(to, "Bienvenue sur IncoKalk", "Bienvenue sur IncoKalk", body);
    }

    private void sendHtml(String to, String subject, String title, String bodyHtml) {
        if (mailSender == null) {
            log.warn("[Mail] MailSender non configuré (spring.mail.username absent) — email '{}' vers {} ignoré", subject, to);
            return;
        }
        try {
            String html = """
                <!DOCTYPE html>
                <html><body style="margin:0;padding:0;background-color:#f9fafb;font-family:Arial,Helvetica,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f9fafb;padding:32px 16px;">
                    <tr><td align="center">
                      <table width="600" cellpadding="0" cellspacing="0" style="background-color:#ffffff;border-radius:12px;overflow:hidden;">
                        <tr><td style="background-color:#2563eb;padding:20px 32px;">
                          <h1 style="color:#ffffff;margin:0;font-size:18px;">IncoKalk</h1>
                        </td></tr>
                        <tr><td style="padding:32px;color:#374151;line-height:1.6;">
                          <h2 style="margin:0 0 16px;color:#111827;font-size:20px;">%s</h2>
                          %s
                        </td></tr>
                        <tr><td style="padding:16px 32px;background-color:#f3f4f6;color:#6b7280;font-size:12px;">
                          IncoKalk — Simulateur Incoterms &amp; douane. Cet email a été envoyé automatiquement, merci de ne pas y répondre.
                        </td></tr>
                      </table>
                    </td></tr>
                  </table>
                </body></html>
                """.formatted(title, bodyHtml);

            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject("[IncoKalk] " + subject);
            helper.setText(html, true);
            mailSender.send(message);
            log.info("[Mail] Email '{}' envoyé à {}", subject, to);
        } catch (Exception e) {
            log.error("[Mail] Erreur envoi email '{}' vers {}: {}", subject, to, e.getMessage());
        }
    }
}
