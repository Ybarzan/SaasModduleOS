package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "taric_rates", indexes = {
    @Index(name = "idx_taric_hs", columnList = "hsCode"),
    @Index(name = "idx_taric_hs_origin", columnList = "hsCode, originCountry"),
    @Index(name = "idx_taric_validity", columnList = "validFrom, validTo")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TaricRate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "hs_code", nullable = false, length = 12)
    private String hsCode;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "origin_country", nullable = false, length = 2)
    private String originCountry;

    @Column(name = "destination_country", nullable = false, length = 2)
    private String destinationCountry;

    @Column(name = "duty_rate", nullable = false)
    private double dutyRate;

    @Column(name = "duty_type", nullable = false, length = 3)
    @Builder.Default
    private String dutyType = "AD"; // AD = ad valorem, SD = specific, MIX = mixed

    @Column(name = "specific_amount")
    private Double specificAmount;

    @Column(name = "specific_unit", length = 20)
    private String specificUnit; // KG, TON, LTR, etc.

    @Column(name = "trade_agreement_code", length = 20)
    private String tradeAgreementCode; // APE code: EVFTA, CETA, etc.

    @Column(name = "is_prefential")
    @Builder.Default
    private boolean isPrefential = false;

    @Column(name = "prefential_origin_criteria", length = 10)
    private String prefentialOriginCriteria; // WH, WO, PE, CTH, CTSH

    @Column(name = "is_anti_dumping")
    @Builder.Default
    private boolean isAntiDumping = false;

    @Column(name = "anti_dumping_duty")
    private Double antiDumpingDuty;

    @Column(name = "valid_from")
    @Builder.Default
    private LocalDate validFrom = LocalDate.now();

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "search_text", length = 500)
    private String searchText; // lowercase, accent-stripped for search

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
