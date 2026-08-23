package com.incokalk.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "carrier_booking_requests")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CarrierBookingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    @JsonIgnore
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnore
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shipment_order_id", nullable = false)
    @JsonIgnore
    private ShipmentOrder shipmentOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "carrier_id", nullable = false)
    @JsonIgnore
    private Carrier carrier;

    @JsonProperty("companyId")
    public UUID getTenantCompanyId() {
        return company.getId();
    }

    @JsonProperty("userId")
    public UUID getRequestedByUserId() {
        return user.getId();
    }

    @JsonProperty("shipmentOrderId")
    public UUID getLinkedShipmentOrderId() {
        return shipmentOrder.getId();
    }

    @JsonProperty("carrierId")
    public UUID getTransportCarrierId() {
        return carrier.getId();
    }

    @Column(name = "carrier_reference", length = 100)
    private String carrierReference;

    @Column(name = "carrier_tracking_number", length = 200)
    private String carrierTrackingNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "carrier_booking_status", nullable = false)
    @Builder.Default
    private BookingStatus carrierBookingStatus = BookingStatus.PENDING;

    @Column(name = "carrier_response_json", columnDefinition = "TEXT")
    private String carrierResponseJson;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "service_type", length = 50)
    private String serviceType;

    @Column(name = "special_instructions", columnDefinition = "TEXT")
    private String specialInstructions;

    @Column(name = "requested_pickup_date")
    private LocalDate requestedPickupDate;

    @Column(name = "estimated_pickup_date")
    private LocalDate estimatedPickupDate;

    @Column(name = "estimated_transit_days")
    private Integer estimatedTransitDays;

    @Column(name = "estimated_delivery_date")
    private LocalDate estimatedDeliveryDate;

    @Column(name = "quoted_cost", precision = 15, scale = 2)
    private BigDecimal quotedCost;

    @Column(name = "quoted_cost_currency", length = 3)
    @Builder.Default
    private String quotedCostCurrency = "EUR";

    /** true si la reponse transporteur vient d'un fallback simule (pas de cle API
     * configuree, ou echec de l'appel reel), false si elle vient vraiment du transporteur. */
    @Column(name = "is_simulated", nullable = false)
    @Builder.Default
    private boolean simulated = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "container_type", length = 30)
    private ShipmentOrder.ContainerType containerType;

    @Column(name = "container_length_m")
    private Double containerLengthM;

    @Column(name = "container_width_m")
    private Double containerWidthM;

    @Column(name = "container_height_m")
    private Double containerHeightM;

    @Column(name = "is_reefers")
    private Boolean isReefers;

    @Column(name = "refrigerated_min_celsius")
    private Integer refrigeratedMinCelsius;

    @Column(name = "refrigerated_max_celsius")
    private Integer refrigeratedMaxCelsius;

    @Column(name = "is_insulated")
    private Boolean isInsulated;

    @Column(name = "is_temperature_monitored")
    private Boolean isTemperatureMonitored;

    @Column(name = "custom_container_spec", columnDefinition = "TEXT")
    private String customContainerSpec;

    @Column(name = "quantity")
    private Integer quantity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum BookingStatus {
        PENDING,
        SUBMITTED,
        CONFIRMED,
        REJECTED,
        CANCELLED,
        COMPLETED,
        FAILED
    }

    public CarrierBookingRequest(Company company, User user, ShipmentOrder shipmentOrder, Carrier carrier) {
        this.company = company;
        this.user = user;
        this.shipmentOrder = shipmentOrder;
        this.carrier = carrier;
    }
}