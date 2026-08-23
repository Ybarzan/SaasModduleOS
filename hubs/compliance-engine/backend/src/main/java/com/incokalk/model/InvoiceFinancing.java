package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "invoice_financing", indexes = {
    @Index(name = "idx_invoice_financing_company", columnList = "company_id"),
    @Index(name = "idx_invoice_financing_invoice", columnList = "invoice_id"),
    @Index(name = "idx_invoice_financing_status", columnList = "status")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class InvoiceFinancing {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "requested_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal requestedAmount;

    @Column(name = "finance_amount", precision = 15, scale = 2)
    private BigDecimal financeAmount;

    @Column(name = "fee_amount", precision = 15, scale = 2)
    private BigDecimal feeAmount;

    @Column(name = "fee_percent", precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal feePercent = new BigDecimal("2.50");

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "requested_at", nullable = false)
    @Builder.Default
    private LocalDateTime requestedAt = LocalDateTime.now();

    @Column(name = "funded_at")
    private LocalDateTime fundedAt;

    @Column(name = "repayment_date")
    private LocalDate repaymentDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Status {
        PENDING, APPROVED, FUNDED, REPAID, REJECTED
    }
}
