package com.incokalk.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "incokalk.ml.embeddings")
public class EmbeddingsConfig {

    /** Base URL of the local Python embeddings service (embeddings-service, FastAPI) */
    private String baseUrl = "http://localhost:8001";

    /** Enable/disable semantic classification (skipped as a source when false) */
    private boolean enabled = true;

    /** Request timeout in milliseconds */
    private int timeoutMs = 10000;
}
