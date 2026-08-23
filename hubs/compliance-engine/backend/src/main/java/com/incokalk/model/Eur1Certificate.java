package com.incokalk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "eur1_certificates", indexes = {
    @Index(name = "idx_eur1_company", columnList = "company_id"),
    @Index(name = "idx_eur1_number", columnList = "certificate_number")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Eur1Certificate {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    @JsonProperty("companyId")
    public UUID getIssuerCompanyId() {
        return company.getId();
    }

    @Column(name = "certificate_number", nullable = false, unique = true, length = 50)
    private String certificateNumber;

    @Column(name = "agreement_code", nullable = false, length = 20)
    private String agreementCode; // e.g. EVFTA, CETA

    @Column(name = "origin_country", nullable = false, length = 2)
    private String originCountry;

    @Column(name = "importer_name", nullable = false)
    private String importerName;

    @Column(name = "exporter_name", nullable = false)
    private String exporterName;

    @Column(name = "hs_code", nullable = false, length = 12)
    private String hsCode;

    @Column(name = "goods_description", length = 500)
    private String goodsDescription;

    @Column(name = "net_weight_kg")
    private Double netWeightKg;

    @Column(name = "gross_weight_kg")
    private Double grossWeightKg;

    @Column(name = "origin_criteria", length = 10)
    private String originCriteria; // WH, WO, PE, CTH, CTSH

    @Column(name = "production_method", length = 200)
    private String productionMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private CertificateStatus status = CertificateStatus.ISSUED;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "issuer_name")
    private String issuerName; // Customs authority or chamber of commerce

    @Column(name = "notes", length = 1000)
    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum CertificateStatus {
        ISSUED, USED, EXPIRED, REVOKED
    }
}
