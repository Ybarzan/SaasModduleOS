package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_intakes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmailIntake {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sender_email", nullable = false, length = 255)
    private String senderEmail;

    @Column(name = "sender_name", length = 255)
    private String senderName;

    @Column(nullable = false, length = 500)
    private String subject;

    @Column(name = "body_preview", columnDefinition = "TEXT")
    private String bodyPreview;

    @Column(length = 200)
    private String origin;

    @Column(length = 200)
    private String destination;

    @Column(name = "goods_description", length = 500)
    private String goodsDescription;

    @Column(name = "estimated_weight", precision = 12, scale = 2)
    private BigDecimal estimatedWeight;

    @Column(name = "estimated_volume", precision = 12, scale = 2)
    private BigDecimal estimatedVolume;

    @Column(name = "incoterm", length = 10)
    private String incoterm;

    @Column(name = "matched_client_id")
    private UUID matchedClientId;

    @Column(name = "matched_company_id")
    private UUID matchedCompanyId;

    @Column(name = "created_shipment_id")
    private UUID createdShipmentId;

    @Column(name = "mailbox_id")
    private UUID mailboxId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private IntakeStatus status = IntakeStatus.PARSED;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    public enum IntakeStatus {
        PARSED,
        CONFIRMED,
        REJECTED,
        SHIPMENT_CREATED,
        FAILED
    }
}
