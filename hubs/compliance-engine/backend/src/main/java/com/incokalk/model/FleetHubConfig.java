package com.incokalk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Identifiants pour appeler l'API REST de fleet-hub (service indépendant, sa
 * propre base -- docs/07-integration-fleet-hub.md). Un compte de service
 * fleet-hub sans 2FA activé est requis : POST /api/auth/login échoue à
 * renvoyer un token si totpRequired=true.
 */
@Entity
@Table(name = "fleethub_configs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FleetHubConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    @JsonIgnore
    private Company company;

    @Column(nullable = false)
    private String name;

    @Column(name = "base_url", nullable = false, length = 500)
    private String baseUrl;

    @Column(nullable = false)
    private String username;

    @JsonIgnore
    @Column(nullable = false, length = 500)
    private String password;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
