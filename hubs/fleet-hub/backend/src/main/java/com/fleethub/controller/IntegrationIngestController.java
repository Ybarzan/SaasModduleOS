package com.fleethub.controller;

import com.fleethub.integration.IngestPayload;
import com.fleethub.integration.IntegrationProperties;
import com.fleethub.integration.IntegrationSyncService;
import com.fleethub.model.Company;
import com.fleethub.service.IntegrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Canal de « push » pour les fournisseurs de données externes (GPS, tachygraphe,
 * carburant) : {@code POST /api/webhooks/ingest}, authentifié par le header
 * {@code X-API-Key}.
 * <p>
 * Deux modes coexistent :
 * <ul>
 *   <li><b>par société (self-service)</b> : la clé correspond à une
 *       {@code IntegrationConfig} active (clé générée sur la page « Intégrations »).
 *       Les données sont rattachées à la société de la clé (jointure scopée).</li>
 *   <li><b>global</b> : clé partagée {@code integration.webhook-api-key}
 *       (mode historique, INTEGRATION.md).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/webhooks/ingest")
@RequiredArgsConstructor
@Tag(name = "Webhook ingest", description = "Canal push pour les fournisseurs de données externes (GPS, tachygraphe, carburant)")
public class IntegrationIngestController {

    private final IntegrationSyncService syncService;
    private final IntegrationProperties props;
    private final IntegrationService integrationService;

    @PostMapping
    @Operation(summary = "Recevoir des données", description = "Endpoint push pour les fournisseurs. Authentifié via X-API-Key (clé par société ou clé globale).")
    public Map<String, Object> ingest(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                      @RequestBody(required = false) IngestPayload payload) {
        String configured = props.getWebhookApiKey();
        IngestPayload data = payload == null ? IngestPayload.empty() : payload;
        boolean globalMatch = configured != null && configured.equals(apiKey);

        // 1. Clé de webhook par société (self-service).
        if (apiKey != null && !apiKey.isBlank() && !globalMatch) {
            Optional<Company> tenant = integrationService.companyByWebhookKey(apiKey);
            if (tenant.isPresent()) {
                Long companyId = tenant.get().getId();
                int positions = syncService.ingestGpsPositions(nonNull(data.positions()), companyId);
                int tacho = syncService.ingestTachographDays(nonNull(data.tachographDays()), companyId);
                int fuel = syncService.ingestFuelTransactions(nonNull(data.fuelTransactions()), companyId);
                return Map.of(
                        "source", "company-webhook",
                        "positionsUpdated", positions,
                        "tachographDaysSaved", tacho,
                        "fuelTransactionsSaved", fuel);
            }
        }

        // 2. Clé globale (historique).
        if (globalMatch) {
            int positions = syncService.ingestGpsPositions(nonNull(data.positions()));
            int tacho = syncService.ingestTachographDays(nonNull(data.tachographDays()));
            int fuel = syncService.ingestFuelTransactions(nonNull(data.fuelTransactions()));
            return Map.of(
                    "source", "global",
                    "positionsUpdated", positions,
                    "tachographDaysSaved", tacho,
                    "fuelTransactionsSaved", fuel);
        }

        if (apiKey == null || apiKey.isBlank()) {
            if (configured == null || configured.isBlank()) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                        "Aucune clé de webhook valide (configurez une intégration sur la page Intégrations).");
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clé API invalide");
    }

    private <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }
}
