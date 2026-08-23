package com.incokalk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "denied_party_checks", indexes = {
        @Index(name = "idx_dps_company", columnList = "company_id"),
        @Index(name = "idx_dps_result", columnList = "result"),
        @Index(name = "idx_dps_checked_name", columnList = "checked_name"),
        @Index(name = "idx_dps_created_at", columnList = "created_at")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeniedPartyCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    @Column(name = "checked_name", nullable = false)
    private String checkedName;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_type", nullable = false)
    private CheckType checkType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CheckResult result = CheckResult.CLEAR;

    @Column(name = "matched_list_name")
    private String matchedListName;

    @Column(name = "matched_entry_id")
    private String matchedEntryId;

    @Column(name = "matched_entry_details", columnDefinition = "TEXT")
    private String matchedEntryDetails;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    @Builder.Default
    private RiskLevel riskLevel = RiskLevel.LOW;

    @Column(name = "country_code")
    private String countryCode;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "checked_by_user_id")
    private UUID checkedByUserId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum CheckType {
        ENTITY, PERSON, ADDRESS, COUNTRY
    }

    public enum CheckResult {
        CLEAR, MATCH, POSSIBLE_MATCH, BLOCKED
    }

    public enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }
}
