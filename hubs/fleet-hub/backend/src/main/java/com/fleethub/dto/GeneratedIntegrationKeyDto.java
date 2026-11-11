package com.fleethub.dto;

/** Clé en clair, renvoyée une seule fois à sa génération (seul son hash est conservé). */
public record GeneratedIntegrationKeyDto(String consumer, String apiKey) {}
