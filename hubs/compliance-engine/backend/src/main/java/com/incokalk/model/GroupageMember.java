package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "groupage_members", indexes = {
    @Index(name = "idx_groupage_members_groupage", columnList = "groupage_id"),
    @Index(name = "idx_groupage_members_shipment", columnList = "shipment_order_id")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class GroupageMember {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "groupage_id", nullable = false)
    private UUID groupageId;

    @Column(name = "shipment_order_id")
    private UUID shipmentOrderId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "external_company", length = 150)
    private String externalCompany;

    @Column(length = 60)
    private String reference;

    @Column(name = "weight_kg", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal weightKg = BigDecimal.ZERO;

    @Column(name = "volume_m3", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal volumeM3 = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
