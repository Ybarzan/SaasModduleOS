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
@Table(name = "ics2_declarations", indexes = {
        @Index(name = "idx_ics2_company", columnList = "company_id"),
        @Index(name = "idx_ics2_status", columnList = "status"),
        @Index(name = "idx_ics2_shipment", columnList = "shipment_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Ics2Declaration {

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
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Ics2Status status = Ics2Status.DRAFT;

    @Column(name = "sender_eori", length = 20)
    private String senderEori;

    @Column(name = "receiver_eori", length = 20)
    private String receiverEori;

    @Column(name = "vessel_name")
    private String vesselName;

    @Column(name = "voyage_number")
    private String voyageNumber;

    @Column(name = "container_number")
    private String containerNumber;

    @Column(name = "hs_code6", length = 6)
    private String hsCode6;

    @Column(name = "goods_description", columnDefinition = "TEXT")
    private String goodsDescription;

    @Column(name = "gross_weight")
    private BigDecimal grossWeight;

    @Column(name = "packages_count")
    private Integer packagesCount;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "response_message", columnDefinition = "TEXT")
    private String responseMessage;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Ics2Status {
        DRAFT, SENT, ACCEPTED, REJECTED, PENDING
    }
}
