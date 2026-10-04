package com.fleethub.dto.tour;

import java.util.List;

public record OrderImportResultDto(
        int rowsRead,
        int ordersCreated,
        int sitesCreated,
        int sitesGeocoded,
        /** Commandes créées mais dont le site n'a pas pu être localisé (non planifiables en l'état). */
        int ordersWithoutLocation,
        List<String> errors
) {}
