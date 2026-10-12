package com.fleethub.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Compte du portail client (shipper/end-customer suivant ses propres
 * expéditions par identifiant/mot de passe) — ne fait PAS partie de FleetHub
 * {@link AppUser} : c'est une authentification séparée et parallèle, jamais un
 * compte staff FleetHub. Porté depuis compliance-engine.
 */
@Entity
@Table(name = "client_users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClientUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    @JsonIgnore
    private String password;

    @Column(name = "full_name")
    private String fullName;

    @Column(length = 50)
    private String phone;

    @Column(name = "is_active")
    @Builder.Default
    private boolean active = true;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
