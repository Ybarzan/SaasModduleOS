package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "trade_agreements")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TradeAgreement {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "code", nullable = false, unique = true, length = 20)
    private String code; // EVFTA, CETA, UE-JP, etc.

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "partner_country", nullable = false, length = 2)
    private String partnerCountry;

    @Column(name = "partner_name", nullable = false, length = 100)
    private String partnerName;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "agreement_type", nullable = false)
    @Builder.Default
    private AgreementType type = AgreementType.FTA;

    @Column(name = "hs_chapters_covered", length = 2000)
    private String hsChaptersCovered; // comma-separated chapters

    @Column(name = "origin_rules", length = 2000)
    private String originRules; // free text description of RoO

    @Column(name = "valid_from")
    @Builder.Default
    private LocalDate validFrom = LocalDate.now();

    @Enumerated(EnumType.STRING)
    @Column(name = "cumulation_type", nullable = false)
    @Builder.Default
    private CumulationType cumulationType = CumulationType.NONE;

    @Column(name = "cumulation_group_code", length = 20)
    private String cumulationGroupCode;

    @Column(name = "allows_rollup")
    @Builder.Default
    private boolean allowsRollup = false;

    @Column(name = "va_threshold_pct")
    private Double vaThresholdPct;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum AgreementType {
        FTA,    // Free Trade Agreement (APE)
        PTA,    // Preferential Trade Agreement
        CU,     // Customs Union
        PSA     // Partial Scope Agreement
    }
}
