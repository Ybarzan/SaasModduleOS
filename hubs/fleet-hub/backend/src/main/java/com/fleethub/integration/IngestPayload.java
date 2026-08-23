package com.fleethub.integration;

import com.fleethub.integration.dto.FuelTransactionDto;
import com.fleethub.integration.dto.GpsPositionDto;
import com.fleethub.integration.dto.TachographDayDto;

import java.util.List;

/**
 * Payload accepté par le webhook de push {@code POST /api/webhooks/ingest} :
 * un fournisseur externe (tachygraphe, GPS, carburant) envoie ses données sans
 * jeton JWT, authentifié par une clé API partagée (header {@code X-API-Key}).
 */
public record IngestPayload(
        List<GpsPositionDto> positions,
        List<TachographDayDto> tachographDays,
        List<FuelTransactionDto> fuelTransactions) {

    public static IngestPayload empty() {
        return new IngestPayload(List.of(), List.of(), List.of());
    }
}
