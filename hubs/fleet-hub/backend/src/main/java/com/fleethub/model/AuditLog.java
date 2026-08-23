package com.fleethub.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Trace d'audit (RGPD / sécurité) : enregistre qui a fait quoi, sur quel tenant,
 * depuis quelle adresse IP, et quand. La colonne company_id est nullable pour
 * les opérations plateforme (SAAS_ADMIN).
 */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_company_time", columnList = "company_id,created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    private Long userId;

    private String username;

    @Column(nullable = false)
    private String action;

    @Column(length = 2000)
    private String detail;

    private String ipAddress;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
