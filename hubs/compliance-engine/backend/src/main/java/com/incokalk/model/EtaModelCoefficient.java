package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "eta_model_coefficients", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"company_id", "feature_name", "feature_value"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EtaModelCoefficient {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(name = "feature_name", nullable = false, length = 50)
    private String featureName;

    @Column(name = "feature_value", nullable = false, length = 100)
    private String featureValue;

    @Column(name = "coefficient", nullable = false, precision = 12, scale = 6)
    private BigDecimal coefficient;

    @Column(name = "samples_count")
    @Builder.Default
    private int samplesCount = 0;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "trained_at")
    private LocalDateTime trainedAt;

    @Column(name = "intercept", precision = 12, scale = 6)
    @Builder.Default
    private BigDecimal intercept = BigDecimal.ZERO;

    @Column(name = "r_squared", precision = 8, scale = 6)
    private BigDecimal rSquared;
}
