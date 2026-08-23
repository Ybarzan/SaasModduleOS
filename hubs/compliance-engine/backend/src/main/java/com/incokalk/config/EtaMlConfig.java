package com.incokalk.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "incokalk.ml.eta")
public class EtaMlConfig {

    /** Base URL of the Python ML service (maritime-delay-predictor) */
    private String baseUrl = "http://localhost:8000";

    /** Enable/disable ML predictions (falls back to heuristic when false) */
    private boolean enabled = true;

    /** Request timeout in milliseconds */
    private int timeoutMs = 5000;

    /** Blend weight: 0.0 = pure heuristic, 1.0 = pure ML */
    private double blendWeight = 0.7;
}
