package com.incokalk.service.integration.fleethub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incokalk.dto.integration.fleethub.FleetHubShipmentEventRequest;
import com.incokalk.dto.integration.fleethub.FleetHubWebhookSettingsDto;
import com.incokalk.model.Company;
import com.incokalk.model.EventOutbox;
import com.incokalk.repository.CompanyRepository;
import com.incokalk.repository.EventOutboxRepository;
import com.incokalk.security.ApiKeyHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

/**
 * Réception opt-in des événements d'expédition (création / changement de
 * statut) depuis fleet-hub, pour réalimenter le moteur de règles
 * (NotificationService.processEvent via EventOutboxProcessor) resté sans
 * producteur depuis le retrait du domaine Transport (voir
 * EventOutboxProcessor). Même principe que l'intégration entrante Praxio de
 * logistix (PraxioIntegrationService) : clé machine-à-machine hashée,
 * générée ici, jamais relue en clair après affichage initial.
 */
@Service
@RequiredArgsConstructor
public class FleetHubWebhookService {

    private final CompanyRepository companyRepository;
    private final EventOutboxRepository eventOutboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public FleetHubWebhookSettingsDto settings(UUID companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        return new FleetHubWebhookSettingsDto(company.isFleethubWebhookEnabled(), null);
    }

    @Transactional
    public FleetHubWebhookSettingsDto enable(UUID companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        // fleethubWebhookKeyHash ne contient qu'un hash (voir ApiKeyHasher) : la
        // clé en clair n'est jamais récupérable une fois générée. Si une clé existe
        // déjà, on ne peut donc plus la renvoyer -- comme settings(), qui ne la
        // renvoie jamais non plus. Seule une activation initiale (première
        // génération) retourne la clé en clair, une seule fois.
        String plainKeyToReturn = null;
        if (company.getFleethubWebhookKeyHash() == null) {
            String generated = Company.generateFleetHubWebhookKey();
            company.setFleethubWebhookKeyHash(ApiKeyHasher.hash(generated));
            plainKeyToReturn = generated;
        }
        company.setFleethubWebhookEnabled(true);
        companyRepository.save(company);
        return new FleetHubWebhookSettingsDto(true, plainKeyToReturn);
    }

    /**
     * La clé stockée n'étant qu'un hash (voir enable()), elle ne peut plus être
     * réaffichée après coup -- on l'efface donc à la désactivation : une
     * réactivation ultérieure en génère une nouvelle plutôt que de laisser une
     * clé orpheline, invisible mais toujours présente en base, sans utilité.
     */
    @Transactional
    public void disable(UUID companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow();
        company.setFleethubWebhookEnabled(false);
        company.setFleethubWebhookKeyHash(null);
        companyRepository.save(company);
    }

    /**
     * Résout la société propriétaire de la clé présentée -- jamais depuis un
     * identifiant fourni par l'appelant. Rejette une clé absente, invalide,
     * ou dont le partage a été désactivé depuis.
     */
    @Transactional(readOnly = true)
    public UUID resolveCompanyId(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clé X-FleetHub-Webhook-Key manquante");
        }
        return companyRepository.findByFleethubWebhookKeyHashAndFleethubWebhookEnabledTrue(ApiKeyHasher.hash(apiKey))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clé invalide ou webhook désactivé"))
                .getId();
    }

    /**
     * Écrit l'événement dans event_outbox, dans la même transaction que
     * cette requête -- livraison garantie par EventOutboxProcessor (relecture
     * périodique des lignes PENDING), pas par la disponibilité immédiate du
     * moteur de notification au moment de l'appel.
     */
    @Transactional
    public void recordEvent(UUID companyId, FleetHubShipmentEventRequest request) {
        try {
            String payload = objectMapper.writeValueAsString(request);
            eventOutboxRepository.save(EventOutbox.builder()
                    .eventType("FLEETHUB_" + request.eventType())
                    .companyId(companyId)
                    .payload(payload)
                    .build());
        } catch (Exception e) {
            throw new IllegalStateException("Échec de sérialisation de l'événement fleet-hub", e);
        }
    }
}
