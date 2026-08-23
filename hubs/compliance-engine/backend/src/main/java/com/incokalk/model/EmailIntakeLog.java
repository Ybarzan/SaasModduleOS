package com.incokalk.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_intake_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EmailIntakeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "email_mailbox_id", nullable = false)
    private EmailMailbox mailbox;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LogStatus status;

    @Column(length = 1000)
    private String message;

    @Column(name = "processed_count", nullable = false)
    @Builder.Default
    private Integer processedCount = 0;

    @Column(name = "error_count", nullable = false)
    @Builder.Default
    private Integer errorCount = 0;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public enum LogStatus { RUNNING, SUCCESS, PARTIAL, FAILED }
}
