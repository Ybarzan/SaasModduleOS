package com.fleethub.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "fleethub.ml.eta")
public class EtaMlConfig {

    /** URL de base du service ML Python (maritime-delay-predictor), si disponible. */
    private String baseUrl = "http://localhost:8000";

    /** Active/désactive les prédictions ML (sinon repli sur l'heuristique). */
    private boolean enabled = true;

    /** Timeout de la requête en millisecondes. */
    private int timeoutMs = 5000;

    /** Poids du mélange : 0.0 = heuristique pure, 1.0 = ML pur. */
    private double blendWeight = 0.7;
}
