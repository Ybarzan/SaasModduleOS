package com.incokalk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "api_keys")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    @JsonIgnore
    private Company company;

    @JsonProperty("userId")
    public UUID getOwnerUserId() {
        return user.getId();
    }

    @JsonProperty("companyId")
    public UUID getTenantCompanyId() {
        return company != null ? company.getId() : null;
    }

    @JsonIgnore
    @Column(name = "key_hash", nullable = false)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false)
    private String keyPrefix;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private User.Plan plan;

    @Column(name = "daily_limit", nullable = false)
    private int dailyLimit;

    @Column(name = "calls_today")
    @Builder.Default
    private int callsToday = 0;

    @Column(name = "total_calls")
    @Builder.Default
    private int totalCalls = 0;

    @Column(name = "last_used")
    private LocalDateTime lastUsed;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
