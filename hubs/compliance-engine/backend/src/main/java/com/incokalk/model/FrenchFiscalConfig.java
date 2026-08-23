package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "french_fiscal_config")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FrenchFiscalConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "eori_number", length = 20)
    private String eoriNumber;

    @Column(name = "default_regime", length = 10)
    private String defaultRegime;

    @Column(name = "tai_applicable")
    @Builder.Default
    private Boolean taiApplicable = true;

    @Column(name = "accises_applicable")
    @Builder.Default
    private Boolean accisesApplicable = true;

    @Column(name = "regime_perfectionnement_actif")
    @Builder.Default
    private Boolean regimePerfectionnementActif = false;

    @Column(name = "vat_rate", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal vatRate = new BigDecimal("20");

    @Column(name = "vat_number", length = 30)
    private String vatNumber;

    @Column(name = "intra_eu_scheme", length = 20)
    @Builder.Default
    private String intraEuScheme = "normal";

    @Column(name = "deb_frequency", length = 20)
    @Builder.Default
    private String debFrequency = "monthly";

    @Column(name = "deb_threshold", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal debThreshold = new BigDecimal("460000");

    @Column(name = "intrastat_dispatch_threshold", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal intrastatDispatchThreshold = new BigDecimal("460000");

    @Column(name = "intrastat_arrival_threshold", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal intrastatArrivalThreshold = new BigDecimal("460000");

    @Column(name = "intrastat_declaration_type", length = 20)
    @Builder.Default
    private String intrastatDeclarationType = "simplified";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
