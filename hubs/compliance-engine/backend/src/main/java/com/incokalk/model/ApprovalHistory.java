package com.incokalk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "approval_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApprovalHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    @JsonIgnore
    private ApprovalRequest request;

    @Column(name = "step_order")
    private Integer stepOrder;

    @Column(name = "step_name")
    private String stepName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Action action;

    @Column(name = "performed_by_user_id", nullable = false)
    private UUID performedByUserId;

    @CreationTimestamp
    @Column(name = "performed_at", updatable = false)
    private LocalDateTime performedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;

    public enum Action {
        APPROVED, REJECTED, DELEGATED, CANCELLED
    }
}
