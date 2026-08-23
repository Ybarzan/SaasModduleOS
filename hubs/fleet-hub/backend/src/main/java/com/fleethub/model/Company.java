package com.fleethub.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Entreprise cliente (tenant) de la plateforme SaaS.
 * Chaque société possède ses propres données (chauffeurs, camions, trajets…),
 * isolées par la colonne company_id présente sur toutes les entités métier.
 */
@Entity
@Table(name = "company")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionPlan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompanyStatus status;

    private LocalDateTime trialEndsAt;

    /** Fournisseur de paiement (stripe, …) — réservé pour la facturation. */
    private String subscriptionProvider;

    private String subscriptionId;

    /** Client Stripe (customer) associé à la société, si la facturation est branchée. */
    private String billingCustomerId;

    /** Dernier envoi d'un rappel d'expiration d'essai (J-7 / J-1). */
    private LocalDateTime lastTrialReminderAt;

    // ---- Coordonnées légales / facturation (RGPD : données client) ----
    private String legalName;
    private String siret;
    private String vatNumber;
    private String address;
    private String postalCode;
    private String city;
    private String country;
    private String contactEmail;
    private String contactPhone;

    private LocalDateTime createdAt;

    public enum SubscriptionPlan {
        TRIAL(10, 5),
        STARTER(25, 10),
        PRO(100, 50),
        ENTERPRISE(null, null);

        private final Integer maxVehicles;
        private final Integer maxDrivers;

        SubscriptionPlan(Integer maxVehicles, Integer maxDrivers) {
            this.maxVehicles = maxVehicles;
            this.maxDrivers = maxDrivers;
        }

        public Integer getMaxVehicles() {
            return maxVehicles;
        }

        public Integer getMaxDrivers() {
            return maxDrivers;
        }
    }

    public enum CompanyStatus {
        TRIAL, ACTIVE, SUSPENDED, CANCELLED
    }

    /** Peut se connecter : les comptes résiliés sont exclus, les essais expirés
     *  et les comptes suspendus restent connectables (pour souscrire ou payer),
     *  mais leurs données sont gelées tant que l'abonnement n'est pas actif. */
    public boolean canLogin() {
        return status == CompanyStatus.ACTIVE
                || status == CompanyStatus.TRIAL
                || status == CompanyStatus.SUSPENDED;
    }

    /** Abonnement effectivement actif : accès aux données métier autorisé. */
    public boolean hasActiveSubscription() {
        return status == CompanyStatus.ACTIVE
                || (status == CompanyStatus.TRIAL
                && (trialEndsAt == null || trialEndsAt.isAfter(LocalDateTime.now())));
    }
}
