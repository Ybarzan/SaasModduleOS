package com.fleethub.service;

import com.fleethub.config.ApiKeyCrypto;
import com.fleethub.model.Company;
import com.fleethub.model.OutboundEvent;
import com.fleethub.repository.CompanyRepository;
import com.fleethub.repository.OutboundEventRepository;
import com.fleethub.scheduling.DistributedJobLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Livre à compliance-engine les événements d'expédition mis en attente dans l'outbox
 * ({@link OutboundEvent}) par {@link ShipmentEventPublisher}.
 * <ul>
 *   <li><b>Après commit</b> : l'appel HTTP se fait ici, hors de toute transaction
 *       métier ; une panne de compliance-engine ne bloque plus le flux d'expédition.</li>
 *   <li><b>Nouvelles tentatives</b> : 30 s, 1 min, 2 min… plafonné à 1 h, puis
 *       abandon ({@code FAILED}) après {@link #MAX_ATTEMPTS} essais. Un admin peut
 *       relancer les échecs (ComplianceEngineWebhookService#retryFailed).</li>
 *   <li><b>Ordre conservé par société</b> : tant qu'un événement plus ancien d'une
 *       société attend, les suivants patientent (un passage « livré » ne doit pas
 *       arriver avant « en transit »).</li>
 *   <li><b>Au moins une fois</b> : un renvoi est possible si la réponse se perd ;
 *       l'en-tête {@code X-FleetHub-Event-Id} (et {@code eventId} dans le corps)
 *       permet à compliance-engine d'ignorer le doublon.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboundEventDispatcher {

    static final String WEBHOOK_PATH = "/api/v1/webhooks/fleethub/shipment-events";
    static final int BATCH_SIZE = 20;
    static final int MAX_ATTEMPTS = 12;
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(30);
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);
    private static final Duration SENT_RETENTION = Duration.ofDays(30);

    private final OutboundEventRepository eventRepository;
    private final CompanyRepository companyRepository;
    private final RestTemplate restTemplate;
    private final ApiKeyCrypto apiKeyCrypto;
    private final DistributedJobLock jobLock;

    @Value("${app.integration.compliance-engine.base-url}")
    private String complianceEngineBaseUrl;

    @Scheduled(fixedDelayString = "${app.integration.compliance-engine.dispatch-interval-ms:10000}",
            initialDelayString = "${app.integration.compliance-engine.dispatch-interval-ms:10000}")
    public void dispatchDue() {
        try {
            // Bail large : un lot de 20 envois à 25 s de délai max chacun tient en 10 min.
            jobLock.runExclusively("outbound-events-dispatch", Duration.ofMinutes(10), this::dispatchBatch);
        } catch (Exception e) {
            log.warn("[Outbox] Passe d'envoi interrompue : {}", e.getMessage());
        }
    }

    /** Une passe d'envoi ; renvoie le nombre d'événements acceptés par compliance-engine. */
    public int dispatchBatch() {
        List<OutboundEvent> due = eventRepository.findDue(LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));
        Set<Long> blockedCompanies = new HashSet<>();
        int sent = 0;
        for (OutboundEvent event : due) {
            Long companyId = event.getCompanyId();
            if (blockedCompanies.contains(companyId)
                    || eventRepository.existsByCompanyIdAndStatusAndIdLessThan(
                            companyId, OutboundEvent.Status.PENDING, event.getId())) {
                blockedCompanies.add(companyId);
                continue;
            }
            switch (deliver(event)) {
                case SENT -> sent++;
                case RETRY -> blockedCompanies.add(companyId);
                case DROPPED -> { /* abandonné : ne bloque pas les suivants */ }
            }
        }
        return sent;
    }

    private enum Outcome { SENT, RETRY, DROPPED }

    private Outcome deliver(OutboundEvent event) {
        Company company = companyRepository.findById(event.getCompanyId()).orElse(null);
        String encryptedKey = company != null ? company.getComplianceEngineWebhookKey() : null;
        if (encryptedKey == null || encryptedKey.isBlank()) {
            // Partage retiré entre la mise en attente et l'envoi : plus personne à qui livrer.
            return fail(event, "Clé webhook compliance-engine retirée avant l'envoi", true);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-FleetHub-Event-Id", event.getEventId().toString());
        try {
            headers.set("X-FleetHub-Webhook-Key", apiKeyCrypto.decrypt(encryptedKey));
            restTemplate.postForEntity(complianceEngineBaseUrl + WEBHOOK_PATH,
                    new HttpEntity<>(event.getPayload(), headers), Void.class);
        } catch (HttpStatusCodeException e) {
            return fail(event, "HTTP " + e.getStatusCode().value() + " : " + e.getResponseBodyAsString(),
                    isPermanent(e.getStatusCode()));
        } catch (Exception e) {
            return fail(event, e.getClass().getSimpleName() + " : " + e.getMessage(), false);
        }
        event.setAttempts(event.getAttempts() + 1);
        event.setStatus(OutboundEvent.Status.SENT);
        event.setSentAt(LocalDateTime.now());
        event.setLastError(null);
        eventRepository.save(event);
        return Outcome.SENT;
    }

    /** 400 / 422 : le corps est refusé tel quel, un renvoi identique échouerait pareil. */
    private static boolean isPermanent(HttpStatusCode status) {
        return status.value() == 400 || status.value() == 422;
    }

    private Outcome fail(OutboundEvent event, String error, boolean permanent) {
        int attempts = event.getAttempts() + 1;
        event.setAttempts(attempts);
        event.setLastError(error.length() > 1000 ? error.substring(0, 1000) : error);
        if (permanent || attempts >= MAX_ATTEMPTS) {
            event.setStatus(OutboundEvent.Status.FAILED);
            log.warn("[Outbox] Événement {} ({}) abandonné après {} essai(s) : {}",
                    event.getEventId(), event.getEventType(), attempts, event.getLastError());
            eventRepository.save(event);
            return Outcome.DROPPED;
        }
        event.setNextAttemptAt(LocalDateTime.now().plus(backoff(attempts)));
        log.info("[Outbox] Événement {} : essai {} échoué, nouvel essai à {} ({})",
                event.getEventId(), attempts, event.getNextAttemptAt(), event.getLastError());
        eventRepository.save(event);
        return Outcome.RETRY;
    }

    /** 30 s après le 1er échec, puis doublement, plafonné à 1 h. */
    static Duration backoff(int attempts) {
        int exponent = Math.min(attempts - 1, 20);
        Duration delay = BASE_BACKOFF.multipliedBy(1L << exponent);
        return delay.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : delay;
    }

    @Scheduled(cron = "0 20 4 * * *")
    @Transactional
    public void purgeSentEvents() {
        int deleted = eventRepository.deleteSentBefore(LocalDateTime.now().minus(SENT_RETENTION));
        if (deleted > 0) {
            log.info("[Outbox] {} événement(s) livré(s) de plus de 30 jours supprimé(s)", deleted);
        }
    }
}
