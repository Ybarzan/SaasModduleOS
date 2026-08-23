package com.fleethub.integration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "integration")
@Getter
@Setter
public class IntegrationProperties {

    /** Clé API partagée pour le webhook de push {@code POST /api/webhooks/ingest}. */
    private String webhookApiKey = "";

    private Tacho tacho = new Tacho();
    private Gps gps = new Gps();
    private Cost cost = new Cost();

    @Getter
    @Setter
    public static class Tacho {
        private boolean enabled = false;
        private String provider = "";
        private String baseUrl = "";
        private String apiKey = "";
        private int syncDaysBack = 7;
    }

    @Getter
    @Setter
    public static class Gps {
        private boolean enabled = false;
        private String provider = "";
        private String baseUrl = "";
        private String apiKey = "";
    }

    @Getter
    @Setter
    public static class Cost {
        private boolean enabled = false;
        private String provider = "";
        private String baseUrl = "";
        private String apiKey = "";
        private int syncDaysBack = 30;
    }
}
