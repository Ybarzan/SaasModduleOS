package com.fleethub.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private String displayName;

    /** Email de contact (pour un utilisateur SaaS, identique au username). */
    private String email;

    @Column(nullable = false)
    private boolean enabled = true;

    private LocalDateTime createdAt;

    /** Token d'invitation (généré par le tenant admin) — null si acceptée. */
    private String inviteToken;

    /** Expiration du lien d'invitation. */
    private LocalDateTime inviteTokenExpiresAt;

    /** Token de réinitialisation de mot de passe — null hors demande en cours. */
    private String resetToken;

    /** Expiration du lien de réinitialisation. */
    private LocalDateTime resetTokenExpiresAt;

    /** Tenant auquel appartient l'utilisateur. Null pour les opérateurs plateforme (SAAS_ADMIN). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    /** Secret TOTP (base32) pour l'authentification à deux facteurs. Null si 2FA non configurée. */
    private String totpSecret;

    /** true si l'utilisateur a activé la 2FA (le secret est validé). */
    @Column(nullable = false)
    private boolean totpEnabled = false;

    /** Échecs de connexion consécutifs depuis la dernière connexion réussie (voir LoginAttemptService). */
    @Column(nullable = false)
    private int failedLoginAttempts = 0;

    /** Connexion refusée jusqu'à cette date après trop d'échecs. Null = compte non verrouillé. */
    private LocalDateTime lockedUntil;

    /** Comptes CHAUFFEUR uniquement : nombre de chiffres du PIN (null = ancien code à 4 chiffres). */
    private Integer pinLength;

    /** Renseigné uniquement pour les comptes de rôle CHAUFFEUR (portail de pointage) :
     *  lie le compte au chauffeur qu'il représente. Le "mot de passe" de ces
     *  comptes est un code PIN (6 chiffres, 4 pour les codes antérieurs), pas un
     *  mot de passe classique. */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id")
    private Driver driver;
}
