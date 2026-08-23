package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bus d'evenements (pattern outbox) -- voir V63 et docs/03-plan-migration.md
 * du monorepo SaasModduleOS. Une ligne = un evenement metier a livrer a un
 * ou plusieurs consommateurs (aujourd'hui : NotificationService), ecrite
 * dans la meme transaction que le changement qui l'a declenche.
 */
@Entity
@Table(name = "event_outbox")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EventOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Builder.Default
    private int attempts = 0;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public enum Status {
        PENDING,
        PROCESSED,
        FAILED
    }
}
