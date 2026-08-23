package com.fleethub.model;

import com.fleethub.model.NotificationRule.AlertType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Notification d'alerte destinée aux utilisateurs du tenant (administrateurs et
 * gestionnaires) : maintenance à échéance, non-conformité tachygraphe, temps de
 * conduite dépassé, usage anormal, etc. {@code userId} est null pour une
 * notification « broadcast » à toute la société.
 */
@Entity
@Table(name = "notification", indexes = {
        @Index(name = "idx_notif_company_read", columnList = "company_id,is_read")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertType type;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String message;

    /** Identifiant de l'entité concernée (camion, chauffeur, …). */
    private Long entityId;

    private String entityType;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
