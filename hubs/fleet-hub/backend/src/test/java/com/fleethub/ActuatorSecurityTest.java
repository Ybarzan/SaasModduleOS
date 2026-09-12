package com.fleethub;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /actuator/prometheus doit rester accessible sans authentification : Prometheus
 * le scrape en conteneur-à-conteneur (jamais exposé hors du réseau Docker — voir
 * SecurityConfig), sans envoyer de JWT. Une régression ici fait taire tout le
 * dashboard de supervision Grafana sans qu'aucune erreur ne soit visible côté app.
 *
 * @AutoConfigureObservability est nécessaire ici : Spring Boot Test remplace par
 * défaut le MeterRegistry réel par un SimpleMeterRegistry pendant les tests
 * (équivalent Spring Boot 3.x de l'ancien @AutoConfigureMetrics), donc sans
 * cette annotation aucun PrometheusMeterRegistry n'existe et /actuator/prometheus
 * n'est jamais enregistré (404), indépendamment de la config de sécurité.
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureObservability
class ActuatorSecurityTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void prometheusEndpoint_isReachableWithoutAuthentication() throws Exception {
        mvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }

    @Test
    void healthEndpoint_isReachableWithoutAuthentication() throws Exception {
        // Pas de statut précis attendu ici : en test, sans Redis réel, /actuator/health
        // peut légitimement remonter DOWN (503) — ce qu'on vérifie, c'est que la sécurité
        // ne bloque jamais l'appel lui-même (pas de 401/403), pas la santé de l'infra.
        mvc.perform(get("/actuator/health"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    if (status == 401 || status == 403) {
                        throw new AssertionError("/actuator/health ne doit jamais exiger d'authentification, reçu " + status);
                    }
                });
    }
}
