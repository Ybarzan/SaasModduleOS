package com.fleethub.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Clé machine-à-machine d'un hub consommateur pour une société (en-tête
 * {@code X-Internal-Integration-Key}). Une seule clé par (société, consommateur) :
 * en générer une nouvelle remplace l'ancienne (rotation). Seul le hash SHA-256 est
 * stocké ; la clé en clair n'est montrée qu'une fois, à la génération.
 */
@Entity
@Table(name = "integration_api_key",
        uniqueConstraints = @UniqueConstraint(name = "uk_integration_key_consumer", columnNames = {"company_id", "consumer"}))
@Getter
@Setter
@NoArgsConstructor
public class IntegrationApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IntegrationConsumer consumer;

    @Column(nullable = false, unique = true, length = 64)
    @JsonIgnore
    private String keyHash;

    /** 4 derniers caractères de la clé, pour la reconnaître dans l'écran de réglage
     *  (null pour la clé historique, dont on ne connaît que le hash). */
    @Column(length = 8)
    private String keyHint;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastUsedAt;
}
