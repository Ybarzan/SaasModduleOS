package com.incokalk.service;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import com.incokalk.model.MobileDevice;
import com.incokalk.model.MobileNotification;
import com.incokalk.repository.MobileDeviceRepository;
import com.incokalk.repository.MobileNotificationRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
@RequiredArgsConstructor
public class PushNotificationService {

    private final MobileNotificationRepository notificationRepo;
    private final MobileDeviceRepository mobileDeviceRepo;

    @Value("${incokalk.push.firebase.credentials-path:}")
    private String firebaseCredentialsPath;

    private boolean firebaseReady = false;

    private final Map<UUID, List<SseEmitter>> userEmitters = new ConcurrentHashMap<>();

    @PostConstruct
    public void initFirebase() {
        if (firebaseCredentialsPath == null || firebaseCredentialsPath.isBlank()) {
            log.info("[Push] Pas de clé Firebase configurée — push mobile désactivé (SSE uniquement)");
            return;
        }
        try {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(new FileInputStream(firebaseCredentialsPath)))
                    .build();
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
            firebaseReady = true;
            log.info("[Push] Firebase initialisé — push mobile actif");
        } catch (Exception e) {
            log.warn("[Push] Échec d'initialisation Firebase, push mobile désactivé: {}", e.getMessage());
        }
    }

    public boolean isPushConfigured() {
        return firebaseReady;
    }

    public SseEmitter subscribe(UUID userId) {
        SseEmitter emitter = new SseEmitter(300_000L);
        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(() -> removeEmitter(userId, emitter));
        emitter.onError(e -> removeEmitter(userId, emitter));
        userEmitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        log.debug("[PushNotification] SSE client subscribed for user={}", userId);
        return emitter;
    }

    private void removeEmitter(UUID userId, SseEmitter emitter) {
        List<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) {
                userEmitters.remove(userId);
            }
        }
    }

    @Transactional
    public MobileNotification sendNotification(UUID userId, UUID companyId, String title, String body, String type, UUID referenceId) {
        MobileNotification notification = MobileNotification.builder()
                .userId(userId)
                .companyId(companyId)
                .title(title)
                .body(body)
                .type(type)
                .referenceId(referenceId)
                .isRead(false)
                .sentAt(LocalDateTime.now())
                .build();
        notification = notificationRepo.save(notification);
        emitToUser(userId, notification);
        sendFcmPush(userId, notification);
        log.debug("[PushNotification] Created notification id={} for user={}", notification.getId(), userId);
        return notification;
    }

    @Transactional
    public void sendShipmentUpdate(UUID userId, UUID companyId, UUID shipmentId, String status) {
        String title = "Expédition mise à jour";
        String body = "Le statut de votre expédition est passé à : " + status;
        sendNotification(userId, companyId, title, body, "SHIPMENT_STATUS", shipmentId);
    }

    public int getUnreadCount(UUID userId) {
        return notificationRepo.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public void markAsRead(UUID notificationId, UUID userId) {
        notificationRepo.markAsRead(notificationId, userId);
    }

    @Transactional
    public void markAllAsRead(UUID userId) {
        notificationRepo.markAllAsRead(userId);
    }

    public List<MobileNotification> getNotifications(UUID userId) {
        return notificationRepo.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional
    public MobileDevice registerDevice(String deviceToken, String platform, String appVersion, UUID userId, UUID companyId) {
        mobileDeviceRepo.findByDeviceToken(deviceToken).ifPresent(d -> {
            mobileDeviceRepo.deleteByDeviceToken(d.getDeviceToken());
        });

        MobileDevice device = MobileDevice.builder()
            .companyId(companyId)
            .userId(userId)
            .deviceToken(deviceToken)
            .platform(MobileDevice.Platform.valueOf(platform.toUpperCase()))
            .appVersion(appVersion)
            .isActive(true)
            .build();
        return mobileDeviceRepo.save(device);
    }

    @Transactional
    public void unregisterDevice(String deviceToken) {
        if (deviceToken != null) {
            mobileDeviceRepo.deleteByDeviceToken(deviceToken);
        }
    }

    private void emitToUser(UUID userId, MobileNotification notification) {
        List<SseEmitter> emitters = userEmitters.get(userId);
        if (emitters == null || emitters.isEmpty()) return;
        for (SseEmitter emitter : emitters) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("id", notification.getId());
                payload.put("title", notification.getTitle());
                payload.put("body", notification.getBody());
                payload.put("type", notification.getType());
                payload.put("referenceId", notification.getReferenceId());
                payload.put("sentAt", notification.getSentAt() != null ? notification.getSentAt().toString() : null);
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(payload));
            } catch (IOException e) {
                emitter.completeWithError(e);
                removeEmitter(userId, emitter);
            }
        }
    }

    private void sendFcmPush(UUID userId, MobileNotification notification) {
        if (!firebaseReady) return;
        List<MobileDevice> devices = mobileDeviceRepo.findByUserIdAndIsActiveTrue(userId);
        for (MobileDevice device : devices) {
            try {
                Message message = Message.builder()
                        .setToken(device.getDeviceToken())
                        .setNotification(Notification.builder()
                                .setTitle(notification.getTitle())
                                .setBody(notification.getBody())
                                .build())
                        .putData("type", notification.getType() != null ? notification.getType() : "")
                        .putData("referenceId", notification.getReferenceId() != null ? notification.getReferenceId().toString() : "")
                        .build();
                FirebaseMessaging.getInstance().send(message);
            } catch (FirebaseMessagingException e) {
                log.warn("[Push] Échec d'envoi FCM au device {}: {}", device.getId(), e.getMessage());
            }
        }
    }
}
