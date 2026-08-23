package com.incokalk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "carrier_invoices", indexes = {
    @Index(name = "idx_carrier_invoice_company", columnList = "company_id"),
    @Index(name = "idx_carrier_invoice_status", columnList = "status"),
    @Index(name = "idx_carrier_invoice_carrier", columnList = "carrier_id"),
    @Index(name = "idx_carrier_invoice_date", columnList = "invoice_date"),
    @Index(name = "idx_carrier_invoice_number", columnList = "invoice_number")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CarrierInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrier_id")
    @JsonIgnore
    private Carrier carrier;

    @Column(name = "invoice_number", nullable = false)
    private String invoiceNumber;

    @Column(name = "invoice_date", nullable = false)
    private LocalDate invoiceDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.RECEIVED;

    @Column(name = "carrier_name")
    private String carrierName;

    @Column(name = "carrier_reference")
    private String carrierReference;

    @Column(name = "total_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 3)
    @Builder.Default
    private String currency = "EUR";

    @Column(name = "exchange_rate", precision = 10, scale = 6)
    @Builder.Default
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(name = "total_amount_eur", precision = 15, scale = 2)
    private BigDecimal totalAmountEur;

    @Column(name = "freight_amount", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal freightAmount = BigDecimal.ZERO;

    @Column(name = "fuel_surcharge", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal fuelSurcharge = BigDecimal.ZERO;

    @Column(name = "security_fee", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal securityFee = BigDecimal.ZERO;

    @Column(name = "handling_fee", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal handlingFee = BigDecimal.ZERO;

    @Column(name = "customs_fee", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal customsFee = BigDecimal.ZERO;

    @Column(name = "other_charges", precision = 15, scale = 2)
    @Builder.Default
    private BigDecimal otherCharges = BigDecimal.ZERO;

    @Column(name = "other_charges_description")
    private String otherChargesDescription;

    @Column(name = "shipment_reference")
    private String shipmentReference;

    @Column(name = "shipment_id")
    private UUID shipmentId;

    @Column(name = "negotiated_rate", precision = 15, scale = 2)
    private BigDecimal negotiatedRate;

    @Column(precision = 15, scale = 2)
    private BigDecimal variance;

    @Column(name = "variance_percent", precision = 8, scale = 2)
    private BigDecimal variancePercent;

    @Column(name = "reconciliation_notes", columnDefinition = "TEXT")
    private String reconciliationNotes;

    @Column(name = "approved_by_user_id")
    private UUID approvedByUserId;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "dispute_reason", columnDefinition = "TEXT")
    private String disputeReason;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<CarrierInvoiceLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum InvoiceStatus {
        DRAFT, RECEIVED, UNDER_REVIEW, APPROVED, PAID, DISPUTED, REJECTED
    }
}
