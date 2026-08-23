package com.incokalk.service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("EmailService — Tests unitaires")
class EmailServiceTest {

    private EmailService service;
    private JavaMailSender mailSender;
    private MimeMessage message;

    @BeforeEach
    void setUp() {
        service = new EmailService();
        mailSender = mock(JavaMailSender.class);
        message = new jakarta.mail.internet.MimeMessage(
            jakarta.mail.Session.getInstance(new java.util.Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        ReflectionTestUtils.setField(service, "mailSender", mailSender);
        ReflectionTestUtils.setField(service, "fromAddress", "noreply@incokalk.com");
        ReflectionTestUtils.setField(service, "frontendUrl", "https://app.incokalk.com");
    }

    @Test
    @DisplayName("Email reset password : lien correct + destinataire")
    void sendPasswordReset_buildsLink() throws Exception {
        service.sendPasswordReset("user@test.com", "token-123");

        verify(mailSender).send(message);
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("user@test.com");
        assertThat(message.getSubject()).contains("Réinitialisation");
        assertThat(contentOf(message)).contains("https://app.incokalk.com/reset-password?token=token-123");
    }

    @Test
    @DisplayName("Email invitation : mot de passe temporaire + lien de connexion")
    void sendTeamInvitation_includesTempPassword() throws Exception {
        service.sendTeamInvitation("new@test.com", "TempPwd2026", "Acme SAS");

        verify(mailSender).send(message);
        String content = contentOf(message);
        assertThat(content).contains("Acme SAS");
        assertThat(content).contains("TempPwd2026");
        assertThat(content).contains("https://app.incokalk.com/login");
    }

    @Test
    @DisplayName("Email bienvenue : salutation par prénom + liens dashboard/calculateur")
    void sendWelcomeEmail_includesGreetingAndLinks() throws Exception {
        service.sendWelcomeEmail("new@test.com", "Marie Dupont");

        verify(mailSender).send(message);
        String content = contentOf(message);
        assertThat(content).contains("Bonjour Marie");
        assertThat(content).contains("https://app.incokalk.com/simulation");
        assertThat(content).contains("https://app.incokalk.com/dashboard");
        assertThat(message.getSubject()).contains("Bienvenue");
    }

    @Test
    @DisplayName("Email bienvenue : salutation générique si le nom est absent")
    void sendWelcomeEmail_noNameFallsBackToGenericGreeting() throws Exception {
        service.sendWelcomeEmail("new@test.com", null);

        String content = contentOf(message);
        assertThat(content).contains("Bonjour,");
        assertThat(content).doesNotContain("Bonjour null");
    }

    @Test
    @DisplayName("MailSender absent → no-op sans erreur")
    void sendPasswordReset_noMailSender() {
        ReflectionTestUtils.setField(service, "mailSender", null);

        assertThatCode(() -> service.sendPasswordReset("user@test.com", "token"))
            .doesNotThrowAnyException();
        verify(mailSender, never()).send(any(MimeMessage.class));
    }

    private String contentOf(MimeMessage msg) throws Exception {
        StringBuilder sb = new StringBuilder();
        extractContent(msg.getContent(), sb);
        return sb.toString();
    }

    private void extractContent(Object content, StringBuilder sb) throws Exception {
        if (content instanceof String s) {
            sb.append(s);
        } else if (content instanceof jakarta.mail.internet.MimeMultipart mp) {
            for (int i = 0; i < mp.getCount(); i++) {
                extractContent(mp.getBodyPart(i).getContent(), sb);
            }
        }
    }
}
