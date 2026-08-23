package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mobile_devices", indexes = {
    @Index(name = "idx_mobile_devices_company", columnList = "company_id"),
    @Index(name = "idx_mobile_devices_user", columnList = "user_id"),
    @Index(name = "idx_mobile_devices_token", columnList = "device_token")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class MobileDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "device_token", nullable = false, length = 500)
    private String deviceToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Platform platform;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "app_version", length = 20)
    private String appVersion;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Platform {
        IOS, ANDROID
    }
}
