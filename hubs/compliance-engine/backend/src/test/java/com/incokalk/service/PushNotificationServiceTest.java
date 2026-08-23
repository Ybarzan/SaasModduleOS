package com.incokalk.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.incokalk.model.MobileDevice;
import com.incokalk.model.MobileNotification;
import com.incokalk.repository.MobileDeviceRepository;
import com.incokalk.repository.MobileNotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.MockedStatic;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PushNotificationService — Tests unitaires")
class PushNotificationServiceTest {

    @Mock MobileNotificationRepository notificationRepo;
    @Mock MobileDeviceRepository mobileDeviceRepo;

    @InjectMocks PushNotificationService service;

    private UUID userId, companyId, notificationId, shipmentId;
    private MobileNotification notification;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        userId = UUID.randomUUID();
        companyId = UUID.randomUUID();
        notificationId = UUID.randomUUID();
        shipmentId = UUID.randomUUID();

        notification = MobileNotification.builder()
                .id(notificationId)
                .userId(userId)
                .companyId(companyId)
                .title("Titre")
                .body("Contenu")
                .type("TEST")
                .isRead(false)
                .sentAt(LocalDateTime.now())
                .build();
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, List<SseEmitter>> emitterMap() {
        return (Map<UUID, List<SseEmitter>>) ReflectionTestUtils.getField(service, "userEmitters");
    }

    // ── subscribe ─────────────────────────────────────────────────────

    @Test
    @DisplayName("Abonnement SSE : ajoute l'emitter à la map interne")
    void subscribe_addsEmitterToMap() {
        SseEmitter emitter = service.subscribe(userId);

        assertThat(emitter).isNotNull();
        assertThat(emitterMap()).containsKey(userId);
        assertThat(emitterMap().get(userId)).hasSize(1).contains(emitter);
    }

    @Test
    @DisplayName("Abonnement SSE : plusieurs emitters pour le même utilisateur")
    void subscribe_multipleEmitters_accumulate() {
        service.subscribe(userId);
        service.subscribe(userId);

        assertThat(emitterMap().get(userId)).hasSize(2);
    }

    // ── removeEmitter (private, invoked via reflection to hit every branch) ──

    @Test
    @DisplayName("removeEmitter : utilisateur non abonné → aucune erreur (branche emitters == null)")
    void removeEmitter_userNotSubscribed_noException() {
        SseEmitter orphanEmitter = new SseEmitter();

        assertThatCode(() -> ReflectionTestUtils.invokeMethod(service, "removeEmitter", userId, orphanEmitter))
                .doesNotThrowAnyException();

        assertThat(emitterMap()).doesNotContainKey(userId);
    }

    @Test
    @DisplayName("removeEmitter : dernier emitter retiré → l'entrée utilisateur est supprimée (branche isEmpty == true)")
    void removeEmitter_lastEmitter_removesUserEntry() {
        SseEmitter emitter = service.subscribe(userId);

        ReflectionTestUtils.invokeMethod(service, "removeEmitter", userId, emitter);

        assertThat(emitterMap()).doesNotContainKey(userId);
    }

    @Test
    @DisplayName("removeEmitter : un emitter restant → l'entrée utilisateur est conservée (branche isEmpty == false)")
    void removeEmitter_otherEmittersRemain_keepsUserEntry() {
        SseEmitter first = service.subscribe(userId);
        SseEmitter second = service.subscribe(userId);

        ReflectionTestUtils.invokeMethod(service, "removeEmitter", userId, first);

        assertThat(emitterMap()).containsKey(userId);
        assertThat(emitterMap().get(userId)).containsExactly(second);
    }

    // ── sendNotification / emitToUser ────────────────────────────────

    @Test
    @DisplayName("Envoi de notification : aucun abonné SSE → sauvegarde sans erreur (branche emitters == null)")
    void sendNotification_noSubscribers_savesAndReturns() {
        when(notificationRepo.save(any(MobileNotification.class))).thenAnswer(i -> i.getArgument(0));

        MobileNotification result = service.sendNotification(userId, companyId, "Titre", "Corps", "TYPE", null);

        assertThat(result).isNotNull();
        assertThat(result.getTitle()).isEqualTo("Titre");
        assertThat(result.getUserId()).isEqualTo(userId);
        verify(notificationRepo).save(any(MobileNotification.class));
    }

    @Test
    @DisplayName("Envoi de notification : liste d'emitters vide → aucun envoi (branche isEmpty == true)")
    void sendNotification_emptyEmitterList_noSendAttempted() {
        emitterMap().put(userId, new CopyOnWriteArrayList<>());
        when(notificationRepo.save(any(MobileNotification.class))).thenAnswer(i -> i.getArgument(0));

        MobileNotification result = service.sendNotification(userId, companyId, "Titre", "Corps", "TYPE", null);

        assertThat(result).isNotNull();
        assertThat(emitterMap().get(userId)).isEmpty();
    }

    @Test
    @DisplayName("Envoi de notification : abonné actif → l'emitter reçoit l'événement")
    void sendNotification_withSubscriber_emitsEvent() throws IOException {
        SseEmitter mockEmitter = mock(SseEmitter.class);
        emitterMap().put(userId, new CopyOnWriteArrayList<>(List.of(mockEmitter)));
        when(notificationRepo.save(any(MobileNotification.class))).thenAnswer(i -> {
            MobileNotification n = i.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });

        service.sendNotification(userId, companyId, "Titre", "Corps", "TYPE", UUID.randomUUID());

        verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
        assertThat(emitterMap()).containsKey(userId);
    }

    @Test
    @DisplayName("Envoi de notification : referenceId absent + abonné actif → l'emitter reçoit l'événement sans NPE")
    void sendNotification_nullReferenceId_withSubscriber_emitsWithoutNPE() throws IOException {
        SseEmitter mockEmitter = mock(SseEmitter.class);
        emitterMap().put(userId, new CopyOnWriteArrayList<>(List.of(mockEmitter)));
        when(notificationRepo.save(any(MobileNotification.class))).thenAnswer(i -> {
            MobileNotification n = i.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });

        assertThatCode(() -> service.sendNotification(userId, companyId, "Titre", "Corps", "TYPE", null))
                .doesNotThrowAnyException();

        verify(mockEmitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("Envoi de notification : échec d'envoi (IOException) → emitter retiré (branche catch + removeEmitter isEmpty==true)")
    void sendNotification_emitterThrowsIOException_removesEmitter() throws IOException {
        SseEmitter failingEmitter = mock(SseEmitter.class);
        doThrow(new IOException("broken pipe")).when(failingEmitter).send(any(SseEmitter.SseEventBuilder.class));
        emitterMap().put(userId, new CopyOnWriteArrayList<>(List.of(failingEmitter)));
        when(notificationRepo.save(any(MobileNotification.class))).thenAnswer(i -> {
            MobileNotification n = i.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });

        service.sendNotification(userId, companyId, "Titre", "Corps", "TYPE", UUID.randomUUID());

        verify(failingEmitter).completeWithError(any(IOException.class));
        assertThat(emitterMap()).doesNotContainKey(userId);
    }

    @Test
    @DisplayName("Envoi de notification : un emitter en échec parmi deux → seul l'emitter valide est conservé (branche removeEmitter isEmpty==false)")
    void sendNotification_oneOfTwoEmittersFails_keepsWorkingEmitter() throws IOException {
        SseEmitter goodEmitter = mock(SseEmitter.class);
        SseEmitter badEmitter = mock(SseEmitter.class);
        doThrow(new IOException("fail")).when(badEmitter).send(any(SseEmitter.SseEventBuilder.class));
        emitterMap().put(userId, new CopyOnWriteArrayList<>(List.of(goodEmitter, badEmitter)));
        when(notificationRepo.save(any(MobileNotification.class))).thenAnswer(i -> {
            MobileNotification n = i.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });

        service.sendNotification(userId, companyId, "Titre", "Corps", "TYPE", UUID.randomUUID());

        verify(goodEmitter).send(any(SseEmitter.SseEventBuilder.class));
        verify(badEmitter).completeWithError(any(IOException.class));
        assertThat(emitterMap().get(userId)).containsExactly(goodEmitter);
    }

    // ── sendShipmentUpdate ────────────────────────────────────────────

    @Test
    @DisplayName("Mise à jour d'expédition : construit la notification attendue")
    void sendShipmentUpdate_buildsExpectedNotification() {
        when(notificationRepo.save(any(MobileNotification.class))).thenAnswer(i -> i.getArgument(0));

        service.sendShipmentUpdate(userId, companyId, shipmentId, "LIVREE");

        verify(notificationRepo).save(argThat(n ->
                "SHIPMENT_STATUS".equals(n.getType()) &&
                n.getBody().contains("LIVREE") &&
                shipmentId.equals(n.getReferenceId()) &&
                userId.equals(n.getUserId())
        ));
    }

    // ── getUnreadCount ────────────────────────────────────────────────

    @Test
    @DisplayName("Nombre de notifications non lues")
    void getUnreadCount_returnsRepoValue() {
        when(notificationRepo.countByUserIdAndIsReadFalse(userId)).thenReturn(4);

        assertThat(service.getUnreadCount(userId)).isEqualTo(4);
    }

    // ── markAsRead / markAllAsRead ───────────────────────────────────

    @Test
    @DisplayName("Marquer une notification comme lue")
    void markAsRead_delegatesToRepo() {
        service.markAsRead(notificationId, userId);

        verify(notificationRepo).markAsRead(notificationId, userId);
    }

    @Test
    @DisplayName("Marquer toutes les notifications comme lues")
    void markAllAsRead_delegatesToRepo() {
        service.markAllAsRead(userId);

        verify(notificationRepo).markAllAsRead(userId);
    }

    // ── getNotifications ──────────────────────────────────────────────

    @Test
    @DisplayName("Liste des notifications d'un utilisateur")
    void getNotifications_returnsRepoList() {
        when(notificationRepo.findByUserIdOrderByCreatedAtDesc(userId)).thenReturn(List.of(notification));

        List<MobileNotification> result = service.getNotifications(userId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(notificationId);
    }

    // ── registerDevice ────────────────────────────────────────────────

    @Test
    @DisplayName("Enregistrement device : aucun device existant → sauvegarde directe")
    void registerDevice_noExistingDevice_savesNew() {
        when(mobileDeviceRepo.findByDeviceToken("token-123")).thenReturn(Optional.empty());
        when(mobileDeviceRepo.save(any(MobileDevice.class))).thenAnswer(i -> i.getArgument(0));

        MobileDevice result = service.registerDevice("token-123", "ios", "1.0.0", userId, companyId);

        assertThat(result).isNotNull();
        assertThat(result.getPlatform()).isEqualTo(MobileDevice.Platform.IOS);
        assertThat(result.getDeviceToken()).isEqualTo("token-123");
        verify(mobileDeviceRepo, never()).deleteByDeviceToken(anyString());
        verify(mobileDeviceRepo).save(any(MobileDevice.class));
    }

    @Test
    @DisplayName("Enregistrement device : device existant avec le même token → suppression puis recréation")
    void registerDevice_existingDevice_deletesThenSavesNew() {
        MobileDevice existing = MobileDevice.builder().deviceToken("token-123").build();
        when(mobileDeviceRepo.findByDeviceToken("token-123")).thenReturn(Optional.of(existing));
        when(mobileDeviceRepo.save(any(MobileDevice.class))).thenAnswer(i -> i.getArgument(0));

        MobileDevice result = service.registerDevice("token-123", "ANDROID", "2.0", userId, companyId);

        assertThat(result.getPlatform()).isEqualTo(MobileDevice.Platform.ANDROID);
        verify(mobileDeviceRepo).deleteByDeviceToken("token-123");
        verify(mobileDeviceRepo).save(any(MobileDevice.class));
    }

    // ── unregisterDevice ──────────────────────────────────────────────

    @Test
    @DisplayName("Désenregistrement device : token fourni → suppression (branche deviceToken != null == true)")
    void unregisterDevice_withToken_deletesDevice() {
        service.unregisterDevice("token-123");

        verify(mobileDeviceRepo).deleteByDeviceToken("token-123");
    }

    @Test
    @DisplayName("Désenregistrement device : token nul → no-op (branche deviceToken != null == false)")
    void unregisterDevice_nullToken_doesNothing() {
        service.unregisterDevice(null);

        verify(mobileDeviceRepo, never()).deleteByDeviceToken(anyString());
    }

    // ── initFirebase / isPushConfigured ──────────────────────────────

    @Test
    @DisplayName("initFirebase : chemin de clé vide → push non configuré, aucune exception")
    void initFirebase_blankPath_leavesUnconfigured() {
        ReflectionTestUtils.setField(service, "firebaseCredentialsPath", "");

        assertThatCode(() -> service.initFirebase()).doesNotThrowAnyException();
        assertThat(service.isPushConfigured()).isFalse();
    }

    @Test
    @DisplayName("initFirebase : fichier de clé introuvable → échec géré, push non configuré")
    void initFirebase_missingFile_leavesUnconfiguredWithoutThrowing() {
        ReflectionTestUtils.setField(service, "firebaseCredentialsPath", "chemin/inexistant.json");

        assertThatCode(() -> service.initFirebase()).doesNotThrowAnyException();
        assertThat(service.isPushConfigured()).isFalse();
    }

    // ── sendFcmPush (via sendNotification) ───────────────────────────

    @Test
    @DisplayName("Envoi de notification : push non configuré → aucune recherche de device (branche firebaseReady == false)")
    void sendNotification_pushNotConfigured_skipsDeviceLookup() {
        when(notificationRepo.save(any(MobileNotification.class))).thenAnswer(i -> i.getArgument(0));

        service.sendNotification(userId, companyId, "Titre", "Corps", "TYPE", null);

        verify(mobileDeviceRepo, never()).findByUserIdAndIsActiveTrue(any());
    }

    @Test
    @DisplayName("Envoi de notification : push configuré → envoi FCM à chaque device actif")
    void sendNotification_pushConfigured_sendsToActiveDevices() throws Exception {
        ReflectionTestUtils.setField(service, "firebaseReady", true);
        when(notificationRepo.save(any(MobileNotification.class))).thenAnswer(i -> {
            MobileNotification n = i.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });
        MobileDevice device = MobileDevice.builder().id(UUID.randomUUID()).deviceToken("token-abc").build();
        when(mobileDeviceRepo.findByUserIdAndIsActiveTrue(userId)).thenReturn(List.of(device));

        FirebaseMessaging mockMessaging = mock(FirebaseMessaging.class);
        try (MockedStatic<FirebaseMessaging> fcmMock = mockStatic(FirebaseMessaging.class)) {
            fcmMock.when(FirebaseMessaging::getInstance).thenReturn(mockMessaging);
            when(mockMessaging.send(any(Message.class))).thenReturn("projects/x/messages/1");

            service.sendNotification(userId, companyId, "Titre", "Corps", "TYPE", null);

            verify(mockMessaging).send(any(Message.class));
        }
    }

    @Test
    @DisplayName("Envoi de notification : push configuré mais échec FCM sur un device → n'interrompt pas le traitement")
    void sendNotification_fcmSendFails_doesNotThrow() throws Exception {
        ReflectionTestUtils.setField(service, "firebaseReady", true);
        when(notificationRepo.save(any(MobileNotification.class))).thenAnswer(i -> {
            MobileNotification n = i.getArgument(0);
            n.setId(UUID.randomUUID());
            return n;
        });
        MobileDevice device = MobileDevice.builder().id(UUID.randomUUID()).deviceToken("token-abc").build();
        when(mobileDeviceRepo.findByUserIdAndIsActiveTrue(userId)).thenReturn(List.of(device));

        FirebaseMessaging mockMessaging = mock(FirebaseMessaging.class);
        try (MockedStatic<FirebaseMessaging> fcmMock = mockStatic(FirebaseMessaging.class)) {
            fcmMock.when(FirebaseMessaging::getInstance).thenReturn(mockMessaging);
            when(mockMessaging.send(any(Message.class)))
                    .thenThrow(mock(com.google.firebase.messaging.FirebaseMessagingException.class));

            assertThatCode(() -> service.sendNotification(userId, companyId, "Titre", "Corps", "TYPE", null))
                    .doesNotThrowAnyException();
        }
    }
}
