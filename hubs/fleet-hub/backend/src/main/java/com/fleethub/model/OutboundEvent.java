package com.fleethub.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Événement à livrer à un autre hub (aujourd'hui : compliance-engine), écrit dans la
 * transaction qui l'a produit puis envoyé par
 * {@link com.fleethub.service.OutboundEventDispatcher}. {@code eventId} accompagne
 * chaque envoi pour que le destinataire ignore un doublon (un renvoi après une
 * réponse perdue est toujours possible : livraison « au moins une fois »).
 */
@Entity
@Table(name = "outbound_event")
@Getter
@Setter
@NoArgsConstructor
public class OutboundEvent {

    public enum Status { PENDING, SENT, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(nullable = false, length = 50)
    private String eventType;

    /** Corps JSON envoyé tel quel (figé à la création de l'événement). */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(length = 1000)
    private String lastError;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime sentAt;
}
