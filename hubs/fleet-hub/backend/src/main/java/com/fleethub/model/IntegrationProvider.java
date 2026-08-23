package com.fleethub.model;

/**
 * Fournisseurs de données externes configurables par le client (self-service).
 * Chaque valeur correspond à un produit ou une famille (GPS, tachygraphe,
 * carburant, transporteur) accessible via une API REST et/ou un webhook de push.
 */
public enum IntegrationProvider {

    GPS_WEBFLEET("GPS", "Webfleet (TomTom)", "https://", true),
    GPS_WIALON("GPS", "Wialon", "https://", true),
    GPS_GEOTAB("GPS", "Geotab", "https://", true),
    GPS_TRACKER("GPS", "Balise / tracker", "https://", true),
    TACHO_TACHOGRAM("TACHOGRAPH", "Tachogram", "https://", true),
    TACHO_AS24("TACHOGRAPH", "AS24 Tak&drive", "https://", false),
    TACHO_TACHOSHARE("TACHOGRAPH", "Webfleet TachoShare", "https://", true),
    DHL("DHL", "DHL (suivi expéditions)", "https://api-eu.dhl.com", true),
    FUEL_AS24("FUEL", "AS24 Infoservice", "https://", true);

    private final String category;
    private final String label;
    private final String defaultBaseUrl;
    private final boolean usesBearerToken;

    IntegrationProvider(String category, String label, String defaultBaseUrl, boolean usesBearerToken) {
        this.category = category;
        this.label = label;
        this.defaultBaseUrl = defaultBaseUrl;
        this.usesBearerToken = usesBearerToken;
    }

    public String getCategory() {
        return category;
    }

    public String getLabel() {
        return label;
    }

    public String getDefaultBaseUrl() {
        return defaultBaseUrl;
    }

    public boolean usesBearerToken() {
        return usesBearerToken;
    }
}
