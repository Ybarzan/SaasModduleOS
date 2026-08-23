package com.fleethub.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Configuration d'intégration d'une société cliente : un fournisseur externe
 * (GPS, tachygraphe, carburant, DHL…), ses identifiants de connexion et son
 * canal de push. {@code apiKey} est chiffré au repos ; {@code webhookKey} est
 * la clé que le fournisseur utilise pour envoyer des données sur
 * {@code POST /api/webhooks/ingest} (générée à la création, montrée une fois
 * au client sur la page « Intégrations »).
 */
@Entity
@Table(name = "integration_config", indexes = {
        @Index(name = "idx_integ_company", columnList = "company_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class IntegrationConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private IntegrationProvider provider;

    /** URL de base de l'API du fournisseur. */
    @Column(length = 500)
    private String baseUrl;

    /** Clé/token d'API du fournisseur, chiffrée au repos. */
    @Column(length = 500)
    private String apiKey;

    /** Options spécifiques au fournisseur (JSON) : chemin de test, compte, etc. */
    @Column(length = 2000)
    private String settings;

    /** Clé d'authentification du canal de push (webhook), en clair côté base. */
    @Column(length = 64)
    private String webhookKey;

    @Column(nullable = false)
    private boolean enabled;

    private LocalDateTime lastTestAt;
    private Boolean lastTestOk;
    @Column(length = 500)
    private String lastTestMessage;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
