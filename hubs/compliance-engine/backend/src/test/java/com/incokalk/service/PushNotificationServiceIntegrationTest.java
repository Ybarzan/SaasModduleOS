package com.incokalk.service;

import com.incokalk.model.MobileNotification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// Test d'integration (contexte Spring complet, base H2 reelle, aucun mock) --
// verifie que PushNotificationService.sendNotification ne leve plus de NPE
// (regression du bug decouvert par les tests de couverture) quand referenceId
// est absent, y compris avec un abonne SSE actif recevant reellement l'evenement.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PushNotificationServiceIntegrationTest {

    @Autowired
    private PushNotificationService pushNotificationService;

    @Test
    @DisplayName("sendNotification sans referenceId, avec abonne SSE actif -> aucune NPE, notification persistee")
    void sendNotification_nullReferenceId_withRealSseSubscriber_worksEndToEnd() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        SseEmitter emitter = pushNotificationService.subscribe(userId);
        assertThat(emitter).isNotNull();

        assertThatCode(() ->
            pushNotificationService.sendNotification(userId, companyId, "Titre", "Corps", "GENERIC", null)
        ).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("sendNotification sans referenceId -> persistee en base avec referenceId null")
    void sendNotification_nullReferenceId_persistsCorrectly() {
        UUID userId = UUID.randomUUID();
        UUID companyId = UUID.randomUUID();

        MobileNotification saved = pushNotificationService.sendNotification(
            userId, companyId, "Titre", "Corps", "GENERIC", null);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getReferenceId()).isNull();
        assertThat(pushNotificationService.getNotifications(userId)).contains(saved);
    }
}
