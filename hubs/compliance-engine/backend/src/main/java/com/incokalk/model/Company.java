package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "companies")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(name = "logo_url")
    private String logoUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Plan plan = Plan.FREE;

    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    // Programme de parrainage -- généré paresseusement (voir CompanyService),
    // pas de backfill en masse à la migration.
    @Column(name = "referral_code", length = 12)
    private String referralCode;

    @Column(name = "referred_by_company_id")
    private UUID referredByCompanyId;

    @Column(name = "referral_reward_granted", nullable = false)
    @Builder.Default
    private boolean referralRewardGranted = false;

    @Column(columnDefinition = "varchar(2000)")
    @Builder.Default
    private String settings = "{}";

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Plan {
        FREE, STARTER, PRO, ENTERPRISE
    }
}
