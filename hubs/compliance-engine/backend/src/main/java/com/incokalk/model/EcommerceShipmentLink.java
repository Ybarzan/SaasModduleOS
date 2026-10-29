package com.incokalk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Corrélation manuelle, saisie par l'opérateur, entre une expédition
 * fleet-hub ({@code fleethubShipmentId}, un simple UUID -- fleet-hub a sa
 * propre base, aucune FK possible) et la commande e-commerce d'origine --
 * Phase 1 J4 (docs/03-plan-migration.md). Permet à EventOutboxProcessor de
 * déclencher automatiquement {@code ECommerceAdapter.markOrderShipped} dès
 * que fleet-hub signale le passage en IN_TRANSIT pour cette expédition.
 */
@Entity
@Table(name = "ecommerce_shipment_links")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EcommerceShipmentLink {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "integration_id", nullable = false)
    private ECommerceIntegration integration;

    @Column(name = "external_order_id", nullable = false, length = 100)
    private String externalOrderId;

    @Column(name = "fleethub_shipment_id", nullable = false)
    private UUID fleethubShipmentId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
