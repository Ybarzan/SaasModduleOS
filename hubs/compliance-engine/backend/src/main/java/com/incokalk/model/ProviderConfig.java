package com.incokalk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "provider_configs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProviderConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    @Column(name = "provider_type", nullable = false, length = 50)
    private String providerType;

    @Column(name = "api_key_encrypted", length = 500)
    private String apiKeyEncrypted;

    @Column(name = "api_secret", length = 500)
    private String apiSecret;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @Builder.Default
    private Integer priority = 0;

    @Column(name = "config_json", columnDefinition = "TEXT")
    private String configJson;

    @Column(name = "last_health_check")
    private LocalDateTime lastHealthCheck;

    @Column(name = "health_status", length = 20)
    @Builder.Default
    private String healthStatus = "UNKNOWN";

    @Column(name = "consecutive_failures")
    @Builder.Default
    private Integer consecutiveFailures = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
