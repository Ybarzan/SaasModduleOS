package com.incokalk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_terms", indexes = {
    @Index(name = "idx_payment_terms_company", columnList = "company_id"),
    @Index(name = "idx_payment_terms_code", columnList = "code")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "days_until_due", nullable = false)
    private Integer daysUntilDue;

    @Column(name = "early_payment_discount_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal earlyPaymentDiscountPercent = BigDecimal.ZERO;

    @Column(name = "early_payment_discount_days")
    @Builder.Default
    private Integer earlyPaymentDiscountDays = 0;

    @Column(name = "late_fee_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal lateFeePercent = BigDecimal.ZERO;

    @Column(name = "is_active")
    @Builder.Default
    private boolean isActive = true;

    @Column(name = "is_default")
    @Builder.Default
    private boolean isDefault = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
