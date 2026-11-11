package com.fleethub.controller;

import com.fleethub.integration.IngestPayload;
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

/**
 * Canal de « push » pour les fournisseurs de données externes (GPS, tachygraphe,
 * carburant) : {@code POST /api/webhooks/ingest}, authentifié par le header
 * {@code X-API-Key}.
 * <p>
 * La clé correspond à une {@code IntegrationConfig} active d'une société (clé
 * générée sur la page « Intégrations ») ; les données sont rattachées à cette
 * société uniquement (jointure scopée). L'ancienne clé partagée
 * {@code integration.webhook-api-key} a été retirée : elle rattachait les données
 * à n'importe quelle société ayant la même immatriculation ou le même permis.
 */
@RestController
@RequestMapping("/api/webhooks/ingest")
@RequiredArgsConstructor
@Tag(name = "Webhook ingest", description = "Canal push pour les fournisseurs de données externes (GPS, tachygraphe, carburant)")
public class IntegrationIngestController {

    private final IntegrationSyncService syncService;
    private final IntegrationService integrationService;

    @PostMapping
    @Operation(summary = "Recevoir des données", description = "Endpoint push pour les fournisseurs. Authentifié via X-API-Key (clé de webhook d'une société).")
    public Map<String, Object> ingest(@RequestHeader(value = "X-API-Key", required = false) String apiKey,
                                      @RequestBody(required = false) IngestPayload payload) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Clé X-API-Key manquante (générez-la sur la page Intégrations).");
        }
        Company tenant = integrationService.companyByWebhookKey(apiKey)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clé API invalide"));
        IngestPayload data = payload == null ? IngestPayload.empty() : payload;
        Long companyId = tenant.getId();
        int positions = syncService.ingestGpsPositions(nonNull(data.positions()), companyId);
        int tacho = syncService.ingestTachographDays(nonNull(data.tachographDays()), companyId);
        int fuel = syncService.ingestFuelTransactions(nonNull(data.fuelTransactions()), companyId);
        return Map.of(
                "source", "company-webhook",
                "positionsUpdated", positions,
                "tachographDaysSaved", tacho,
                "fuelTransactionsSaved", fuel);
    }

    private <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list;
    }
}
