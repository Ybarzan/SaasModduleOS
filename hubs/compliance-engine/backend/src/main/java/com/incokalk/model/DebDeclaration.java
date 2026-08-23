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
@Table(name = "deb_declarations", indexes = {
        @Index(name = "idx_deb_company", columnList = "company_id"),
        @Index(name = "idx_deb_period", columnList = "period"),
        @Index(name = "idx_deb_status", columnList = "status"),
        @Index(name = "idx_deb_shipment", columnList = "shipment_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DebDeclaration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_id")
    @JsonIgnore
    private ShipmentOrder shipment;

    @Column(name = "declaration_number")
    private String declarationNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private DebType declarationType = DebType.DEB_INTRODUCTION;

    @Column(nullable = false, length = 7)
    private String period;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private DebStatus status = DebStatus.DRAFT;

    @Column(name = "partner_country", length = 2)
    private String partnerCountry;

    @Column(name = "nature_of_transaction", length = 2)
    private String natureOfTransaction;

    @Column(name = "mode_of_transport", length = 2)
    private String modeOfTransport;

    @Column(name = "net_mass")
    private BigDecimal netMass;

    @Column(name = "statistical_value")
    private BigDecimal statisticalValue;

    @Column(name = "hs_code8", length = 8)
    private String hsCode8;

    @Column(name = "goods_description", columnDefinition = "TEXT")
    private String goodsDescription;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum DebType {
        DEB_EXPEDITION, DEB_INTRODUCTION, INTRASTAT_ARRIVAL, INTRASTAT_DEPARTURE
    }

    public enum DebStatus {
        DRAFT, VALIDATED, SUBMITTED
    }
}
