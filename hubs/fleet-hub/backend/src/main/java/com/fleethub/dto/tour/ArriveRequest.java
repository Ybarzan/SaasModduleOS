package com.fleethub.dto.tour;

import java.time.LocalDateTime;

/** Arrivée sur site ; {@code occurredAt} = heure réelle si l'action a été faite hors connexion. */
public record ArriveRequest(LocalDateTime occurredAt) {}
