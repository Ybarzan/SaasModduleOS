package com.fleethub.dto.tour;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** Ordre manuel : liste complète des identifiants d'arrêts dans l'ordre souhaité. */
public record ReorderRequest(@NotEmpty List<Long> stopIds) {}
