package com.fleethub.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/** Cache court-terme des réponses brutes des providers de devis transport (DHL, Shippo, …). */
@Entity
@Table(name = "carrier_rate_cache")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CarrierRateCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "provider_type", nullable = false, length = 50)
    private String providerType;

    @Column(name = "cache_key", nullable = false, unique = true, length = 255)
    private String cacheKey;

    @Column(name = "response_json", nullable = false, columnDefinition = "TEXT")
    private String responseJson;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
