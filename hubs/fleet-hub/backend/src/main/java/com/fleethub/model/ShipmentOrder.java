package com.fleethub.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Commande d'expédition multimodale (carrier/shipment/tracking) — cœur du
 * domaine Transport porté depuis compliance-engine (com.incokalk.ShipmentOrder).
 * <p>
 * Adaptations faites lors de la fusion dans FleetHub :
 * <ul>
 *   <li>{@code company}/{@code user} repointent vers les entités FleetHub (Long/IDENTITY)
 *       au lieu des entités UUID de compliance-engine.</li>
 *   <li>Le champ texte {@code fleetHubTruckRegistration} (ancienne référence HTTP
 *       cross-hub vers un camion FleetHub par plaque) est remplacé par une vraie
 *       relation JPA {@link Truck} — les deux hubs vivent maintenant dans la même
 *       application, plus besoin d'un pont HTTP.</li>
 *   <li>La relation {@code eori} (EoriNumber, entité douane propre à
 *       compliance-engine) est abandonnée : la conformité EORI/douane reste dans
 *       compliance-engine, pas dans FleetHub.</li>
 * </ul>
 */
@Entity
@Table(name = "shipment_orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ShipmentOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    @JsonIgnore
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnore
    private AppUser user;

    // Compte client du portail auquel cette expédition est rattachée (optionnel).
    // NULL = non assignée → invisible dans le portail client.
    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "order_number", nullable = false, unique = true, length = 50)
    private String orderNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private Status status = Status.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrier_id")
    @JsonIgnore
    private Carrier carrier;

    /** Camion de la flotte propre (FleetHub) assigné, si cette expédition est
     * livrée en interne plutôt que par un transporteur tiers ({@code carrier}).
     * Relation JPA directe désormais que fleet-hub et le domaine Transport
     * vivent dans la même application (voir LiveTrackingService). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "truck_id")
    private Truck truck;

    @com.fasterxml.jackson.annotation.JsonProperty("carrierId")
    public UUID getCarrierId() {
        return carrier != null ? carrier.getId() : null;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("carrierName")
    public String getCarrierName() {
        return carrier != null ? carrier.getName() : null;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("truckId")
    public Long getTruckId() {
        return truck != null ? truck.getId() : null;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("truckRegistration")
    public String getTruckRegistration() {
        return truck != null ? truck.getRegistration() : null;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipping_rate_id")
    @JsonIgnore
    private ShippingRate shippingRate;

    @com.fasterxml.jackson.annotation.JsonProperty("transportMode")
    public String getTransportMode() {
        return shippingRate != null ? shippingRate.getTransportMode() : null;
    }

    // Shipper info
    @Column(name = "shipper_name")
    private String shipperName;

    @Column(name = "shipper_address", columnDefinition = "TEXT")
    private String shipperAddress;

    @Column(name = "shipper_city")
    private String shipperCity;

    @Column(name = "shipper_country", length = 3)
    private String shipperCountry;

    @Column(name = "shipper_postal_code", length = 20)
    private String shipperPostalCode;

    // Consignee info
    @Column(name = "consignee_name")
    private String consigneeName;

    @Column(name = "consignee_address", columnDefinition = "TEXT")
    private String consigneeAddress;

    @Column(name = "consignee_city")
    private String consigneeCity;

    @Column(name = "consignee_country", length = 3)
    private String consigneeCountry;

    @Column(name = "consignee_postal_code", length = 20)
    private String consigneePostalCode;

    // Cargo
    @Column(name = "goods_description", columnDefinition = "TEXT")
    private String goodsDescription;

    @Column(name = "goods_value")
    private Double goodsValue;

    @Column(length = 3)
    @Builder.Default
    private String currency = "EUR";

    @Column(name = "weight_kg")
    private Double weightKg;

    @Column(name = "volume_m3")
    private Double volumeM3;

    @Column(name = "packages_count")
    @Builder.Default
    private Integer packagesCount = 1;

    @Column(name = "hs_code", length = 10)
    private String hsCode;

    @Column(name = "incoterm_code", length = 5)
    private String incotermCode;

    @Column(name = "is_dangerous")
    @Builder.Default
    private boolean isDangerous = false;

    @Column(name = "country_of_origin", length = 2)
    private String countryOfOrigin;

    // Container dimensions and type
    @Enumerated(EnumType.STRING)
    @Column(name = "container_type", length = 30)
    @Builder.Default
    private ContainerType containerType = ContainerType.DRY_20FT;

    @Column(name = "container_length_m")
    private Double containerLengthM;

    @Column(name = "container_width_m")
    private Double containerWidthM;

    @Column(name = "container_height_m")
    private Double containerHeightM;

    @Column(name = "refrigerated_min_celsius")
    private Integer refrigeratedMinCelsius;

    @Column(name = "refrigerated_max_celsius")
    private Integer refrigeratedMaxCelsius;

    @Column(name = "is_insulated")
    @Builder.Default
    private boolean isInsulated = false;

    @Column(name = "is_temperature_monitored")
    @Builder.Default
    private boolean isTemperatureMonitored = false;

    @Column(name = "custom_container_spec", columnDefinition = "TEXT")
    private String customContainerSpec;

    public enum ContainerType {
        DRY_20FT, DRY_40FT, REEFER_20FT, REEFER_40FT,
        TANDEM_40FT, FLAT_RAIL, OPEN_TOP_DRY, OPEN_TOP_REEFER,
        FRIGO_40FT, ISO_45, CUSTOM
    }

    // Customs (résumé scalaire ; la conformité EORI/douane détaillée reste côté compliance-engine)
    @Column(name = "customs_declaration_number", length = 50)
    private String customsDeclarationNumber;

    @Column(name = "customs_status", length = 30)
    @Builder.Default
    private String customsStatus = "NONE";

    @Column(name = "duty_amount")
    private Double dutyAmount;

    @Column(name = "vat_amount")
    private Double vatAmount;

    @Column(name = "landed_cost")
    private Double landedCost;

    // Cost
    @Column(name = "quoted_cost")
    private Double quotedCost;

    @Column(name = "final_cost")
    private Double finalCost;

    @Column(name = "cost_currency", length = 3)
    @Builder.Default
    private String costCurrency = "EUR";

    // Dates
    @Column(name = "requested_pickup_date")
    private LocalDateTime requestedPickupDate;

    @Column(name = "estimated_delivery_date")
    private LocalDateTime estimatedDeliveryDate;

    @Column(name = "actual_delivery_date")
    private LocalDateTime actualDeliveryDate;

    @Column(name = "booked_at")
    private LocalDateTime bookedAt;

    @Column(name = "shipped_at")
    private LocalDateTime shippedAt;

    /** MMSI du navire suivi transportant cette expédition, saisi à la main par
     *  un opérateur -- rien ne permet de le déduire automatiquement. Utilisé
     *  pour capter l'ETA diffusée par ce navire lui-même via AIS (voir
     *  AisStreamService.getBroadcastEta, EtaReportedSource.Source.AIS_BROADCAST). */
    @Column(name = "tracked_vessel_mmsi", length = 20)
    private String trackedVesselMmsi;

    @OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<TrackingEvent> trackingEvents = List.of();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public enum Status {
        DRAFT, QUOTED, BOOKED, IN_TRANSIT, DELIVERED, CANCELLED
    }
}
